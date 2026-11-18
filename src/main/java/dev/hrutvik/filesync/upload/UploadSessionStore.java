
# Distributed File Synchronization Platform

A production-minded Java 17 control plane for synchronizing large file trees across intermittently connected devices. The project tackles the hard backend problems behind file sync: content-addressed storage, optimistic concurrency, durable tombstones, idempotent device operations, immutable version history, conflict visibility, and reliable change publication.

## Current checkpoint

- Tenant-scoped file namespace and immutable version ledger
- SHA-256 content-addressed blob references
- Durable multipart upload sessions with direct-to-S3 presigned part URLs
- Replay-safe session creation and compensating multipart aborts
- Completion checks for expected object size and declared hash metadata
- Streamed server-side SHA-256 verification with leased, retryable workers
- Blob verification status polling and a hard commit gate for unverified bytes
- Compare-and-set updates using client `baseVersion`
- Explicit `409 VERSION_CONFLICT` for stale devices
- Payload-aware idempotent device operations
- Durable delete tombstones
- Transactional file-change outbox
- Horizontally scalable leased Kafka relay using `FOR UPDATE SKIP LOCKED`
- At-least-once publication with stable tenant/file keys and event headers
- Tenant-scoped incremental change feed with monotonic cursors
- Bounded cursor pagination for reconnecting and offline devices
- PostgreSQL constraints and lookup indexes
- Path normalization and traversal protection
- Java 17, Spring Boot, Flyway, PostgreSQL, Kafka, and MinIO
- Unit tests, Docker configuration, CI/CD, API examples, and architecture decisions

## First sync flow

1. A client hashes file bytes and creates an idempotent multipart session.
2. The API returns short-lived presigned part URLs, so file bytes bypass application servers.
3. After all parts are uploaded, completion validates object size and hash metadata and queues cryptographic verification.
4. A leased worker streams the object through SHA-256; the client polls until verification is `VERIFIED`.
5. The client commits metadata with the last server version it observed; unverified content receives `409 BLOB_NOT_VERIFIED`.
6. PostgreSQL locks the path head, rejects stale bases, appends the immutable version, advances the head, and writes an outbox event atomically.
7. Downstream device feeds consume the stable change event; retries are safe through the operation ID.

On reconnect, a device calls `GET /v1/changes?after=<cursor>`. It applies the ordered page locally and advances only to the returned `nextCursor`. Tombstones travel through the same feed, so a device cannot accidentally resurrect content deleted while it was offline.

```mermaid
flowchart LR
  D["Device"] --> B["S3-compatible blob store"]
  D --> API["Version API"]
  B --> V["Leased verifier"]
  V --> API
  API --> DB[("PostgreSQL versions")]
  API --> O["Transactional outbox"]
  O --> R["Leased relay"]
  R --> K["Kafka change stream"]
  DB --> C["Cursor change feed"]
```

## Run locally

```bash
docker compose up -d
mvn spring-boot:run
```

See [API examples](api-examples.http) and [architecture decisions](docs/architecture.md).

## Engineering signals for recruiters

This repository demonstrates offline-first concurrency design, immutable data modeling, database-enforced idempotency, transactional event publication, content-addressed storage, and explicit failure semantics. The design is original and based on public distributed-systems patterns; it contains no proprietary GoDaddy or Airtel code.
__END_0__
### Start an idempotent two-part upload and receive presigned URLs
POST http://localhost:8080/v1/upload-sessions
X-Tenant-Id: acme
Content-Type: application/json

{"operationId":"upload-device-a-001","contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sizeBytes":10485760,"partCount":2}

### Complete after uploading each part directly to object storage
POST http://localhost:8080/v1/upload-sessions/{{uploadSessionId}}/complete
X-Tenant-Id: acme
Content-Type: application/json

{"parts":[{"partNumber":1,"eTag":"{{part1Etag}}"},{"partNumber":2,"eTag":"{{part2Etag}}"}]}

### Poll until verificationStatus is VERIFIED before committing metadata
GET http://localhost:8080/v1/upload-sessions/{{uploadSessionId}}
X-Tenant-Id: acme

### Commit a new content-addressed version
POST http://localhost:8080/v1/files/versions
X-Tenant-Id: acme
Content-Type: application/json

{"operationId":"device-a-op-001","deviceId":"11111111-1111-1111-1111-111111111111","path":"docs/architecture.pdf","contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","sizeBytes":1048576,"baseVersion":null}

### Update from the known head; a stale baseVersion returns 409
POST http://localhost:8080/v1/files/versions
X-Tenant-Id: acme
Content-Type: application/json

{"operationId":"device-a-op-002","deviceId":"11111111-1111-1111-1111-111111111111","path":"docs/architecture.pdf","contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","sizeBytes":1049000,"baseVersion":1}

### Create a tombstone so offline devices do not resurrect deleted content
POST http://localhost:8080/v1/files/docs/architecture.pdf/delete
X-Tenant-Id: acme
Content-Type: application/json

{"operationId":"device-a-op-003","deviceId":"11111111-1111-1111-1111-111111111111","baseVersion":2}

### Incremental device catch-up; persist nextCursor after applying the page
GET http://localhost:8080/v1/changes?after=0&limit=100
X-Tenant-Id: acme
__END_1__
# Architecture decisions

## Initial boundary

The control plane records immutable file versions and durable change events. File bytes live in S3-compatible object storage under their SHA-256 content hash; PostgreSQL owns namespace, version, conflict, and tombstone correctness. Kafka distributes committed changes to other devices and indexing consumers.

## Decisions

1. **Content-addressed blobs.** A SHA-256 key deduplicates identical bytes and makes integrity verifiable. The database never stores large file bodies.
2. **Compare-and-set head updates.** Every update names the version it was based on. A stale base produces an explicit `409 VERSION_CONFLICT`; the server never silently overwrites another device's work.
3. **Immutable version history.** Each accepted mutation appends a version, then advances the locked path head in the same transaction.
4. **Durable tombstones.** Deletes are versions, not row removal, preventing offline devices from resurrecting old content when they reconnect.
5. **Payload-aware operations.** `(tenant, operationId)` is unique. Exact retries return the original result, while changed reuse conflicts.
6. **Transactional outbox.** The version and its `FILE_VERSION_COMMITTED` or `FILE_DELETED` event commit atomically. Kafka publication can retry independently.
7. **Tenant-local namespaces.** Paths are unique within a tenant, normalized before persistence, and cannot traverse parents.

## Direct multipart blob transfer

Application instances do not proxy large file bodies. A client first creates an idempotent upload session from the SHA-256 digest, total byte size, and part count. The service initiates an S3 multipart upload and returns short-lived presigned URLs for the exact part numbers. This keeps data-plane bandwidth away from the control plane and works with AWS S3 or local MinIO.

The durable session stores the remote upload ID, expiry, request fingerprint, and content-addressed object key. If database persistence fails after remote initiation, the service issues a compensating abort. Concurrent duplicate operation IDs are protected by a tenant-scoped uniqueness constraint; any losing remote upload is also aborted. Exact request retries return the existing session, while changed payload reuse conflicts.

Completion requires every part exactly once and uses the client-returned ETags. The service checks the S3 object size and declared `content-sha256` metadata before marking the session complete. Because multipart ETags are not content hashes and object metadata is client-declared, neither proves byte integrity.

Completed sessions therefore enter a persisted verification state machine: `PENDING → VERIFYING → VERIFIED|REJECTED`. Worker replicas lease candidates with `FOR UPDATE SKIP LOCKED`, release the database transaction, and stream each object through SHA-256 without loading it into memory. A matching digest marks the blob verified; mismatch is terminal and transport failures release the lease for retry. Expired leases recover crashed workers. The upload-session GET endpoint exposes progress, and file-version commits accept only hashes backed by a `VERIFIED` session. Idempotent commit replays are checked before this gate so a later retention change cannot break an already accepted retry.

## Reliable change publication

File metadata and its outbox row commit in one PostgreSQL transaction. Relay replicas claim unpublished events in batches with `FOR UPDATE SKIP LOCKED`, write a time-bounded lease, and release the database transaction before contacting Kafka. A successful broker acknowledgement marks the event published; a send failure releases the lease immediately, while a crashed worker is recovered after lease expiry.

Kafka delivery remains intentionally at least once. Records use `tenantId:fileId` as their key and carry stable `event-id`, `event-type`, and `tenant-id` headers. Consumers must keep a durable inbox keyed by event ID. This avoids claiming end-to-end exactly-once behavior across PostgreSQL, Kafka, devices, and arbitrary downstream services.

## Incremental device catch-up

Every immutable file version receives a database-generated monotonic `change_sequence`. A reconnecting device requests changes strictly after its last durable cursor. The service queries by `(tenant_id, change_sequence)`, returns a bounded page, and supplies the last returned sequence as `nextCursor`. Fetching one extra row determines `hasMore` without an expensive count.

The sequence is a cursor, not a promise of gap-free tenant-local numbering: other tenants share the global identity and rolled-back transactions can consume values. Devices care only about ordering and `>` semantics. They persist the new cursor after applying the full page locally. Because delete tombstones are ordinary versions, offline clients learn deletions through the same mechanism.

## Evolution plan

Upcoming checkpoints add device inbox deduplication, conflict-copy policy, directory moves, quotas, Redis hot manifests, garbage collection, S3 event reconciliation, security, tracing, load tests, AWS deployment, and SLO dashboards.
__END_2__
spring:
  application.name: distributed-file-synchronization-platform
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/filesync}
    username: ${DB_USER:filesync}
    password: ${DB_PASSWORD:filesync}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      acks: all
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
management.endpoints.web.exposure.include: health,info,prometheus
filesync:
  blob:
    bucket: ${BLOB_BUCKET:filesync-blobs}
    region: ${AWS_REGION:us-east-1}
    endpoint: ${BLOB_ENDPOINT:http://localhost:9000}
    access-key: ${AWS_ACCESS_KEY_ID:filesync}
    secret-key: ${AWS_SECRET_ACCESS_KEY:filesync-secret}
    presign-ttl: ${PRESIGN_TTL:PT15M}
  upload.session-ttl: ${UPLOAD_SESSION_TTL:PT1H}
  verification:
    batch-size: ${VERIFICATION_BATCH_SIZE:20}
    poll-delay: ${VERIFICATION_POLL_DELAY:2000}
    lease-duration: ${VERIFICATION_LEASE_DURATION:PT5M}
  outbox:
    topic: ${FILE_CHANGE_TOPIC:filesync.changes.v1}
    batch-size: ${OUTBOX_BATCH_SIZE:100}
    poll-delay: ${OUTBOX_POLL_DELAY:1000}
    lease-duration: ${OUTBOX_LEASE_DURATION:PT30S}
    send-timeout: ${OUTBOX_SEND_TIMEOUT:PT5S}
__END_3__
alter table upload_sessions add column verification_status varchar(20) not null default 'PENDING' check (verification_status in ('PENDING','VERIFYING','VERIFIED','REJECTED'));
alter table upload_sessions add column verification_lease_owner varchar(100);
alter table upload_sessions add column verification_lease_until timestamptz;
alter table upload_sessions add column verified_at timestamptz;
alter table upload_sessions add column verification_failure varchar(500);
create index idx_upload_sessions_verification on upload_sessions(completed_at)
 where status='COMPLETED' and verification_status in ('PENDING','VERIFYING');
__END_4__
package dev.hrutvik.filesync.blob;
import java.io.*; import java.security.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component; import software.amazon.awssdk.services.s3.S3Client; import software.amazon.awssdk.services.s3.model.*; import software.amazon.awssdk.services.s3.presigner.S3Presigner; import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest; import java.time.Duration; import java.util.*;
@Component public class MultipartBlobGateway {
 private final S3Client s3; private final S3Presigner presigner; private final String bucket; private final Duration ttl;
 public MultipartBlobGateway(S3Client s3,S3Presigner presigner,@Value("${filesync.blob.bucket}") String bucket,@Value("${filesync.blob.presign-ttl:PT15M}") Duration ttl){this.s3=s3;this.presigner=presigner;this.bucket=bucket;this.ttl=ttl;}
 public RemoteUpload initiate(String key,String hash){return new RemoteUpload(key,s3.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket(bucket).key(key).metadata(Map.of("content-sha256",hash)).build()).uploadId());}
 public List<PartUpload> presign(String key,String uploadId,int count){var result=new ArrayList<PartUpload>();for(int part=1;part<=count;part++){var request=UploadPartRequest.builder().bucket(bucket).key(key).uploadId(uploadId).partNumber(part).build();var signed=presigner.presignUploadPart(UploadPartPresignRequest.builder().signatureDuration(ttl).uploadPartRequest(request).build());result.add(new PartUpload(part,signed.url().toString()));}return result;}
 public void complete(String key,String uploadId,List<CompletedUploadPart> parts){var completed=parts.stream().map(p->CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build()).toList();s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId).multipartUpload(CompletedMultipartUpload.builder().parts(completed).build()).build());}
 public BlobHead head(String key){var h=s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());return new BlobHead(h.contentLength(),h.metadata().get("content-sha256"));}
 public String sha256(String key){try(var input=s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())){var digest=MessageDigest.getInstance("SHA-256");input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(),digest));return HexFormat.of().formatHex(digest.digest());}catch(IOException|NoSuchAlgorithmException e){throw new IllegalStateException("could not verify blob",e);}}
 public void abort(String key,String uploadId){s3.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId).build());}
 public record RemoteUpload(String objectKey,String uploadId){} public record PartUpload(int partNumber,String url){} public record CompletedUploadPart(int partNumber,String eTag){} public record BlobHead(long sizeBytes,String declaredHash){}
}
__END_5__
package dev.hrutvik.filesync.upload;
import dev.hrutvik.filesync.blob.MultipartBlobGateway.PartUpload; import java.time.Instant; import java.util.*;
public record UploadSessionResponse(UUID sessionId,String contentHash,long sizeBytes,int partCount,String status,String verificationStatus,Instant expiresAt,List<PartUpload> parts,boolean replayed) {}
__END_6__
package dev.hrutvik.filesync.upload;
import org.springframework.jdbc.core.simple.JdbcClient; import org.springframework.stereotype.Repository; import java.time.Instant; import java.util.*;
@Repository public class UploadSessionStore {
 private final JdbcClient db; public UploadSessionStore(JdbcClient db){this.db=db;}
 public Optional<Session> findByOperation(String tenant,String operation){return db.sql("select * from upload_sessions where tenant_id=:t and operation_id=:o").param("t",tenant).param("o",operation).query(this::map).optional();}
 public Optional<Session> lock(UUID id,String tenant){return db.sql("select * from upload_sessions where id=:id and tenant_id=:t for update").param("id",id).param("t",tenant).query(this::map).optional();}
 public Optional<Session> find(UUID id,String tenant){return db.sql("select * from upload_sessions where id=:id and tenant_id=:t").param("id",id).param("t",tenant).query(this::map).optional();}
 public Session insert(String tenant,String operation,String fingerprint,String hash,long size,int count,String key,String remoteId,Instant expires){UUID id=UUID.randomUUID();db.sql("insert into upload_sessions(id,tenant_id,operation_id,request_fingerprint,content_hash,size_bytes,part_count,object_key,remote_upload_id,status,created_at,expires_at) values(:id,:t,:o,:f,:h,:s,:c,:k,:r,'READY',now(),:e)").params(Map.of("id",id,"t",tenant,"o",operation,"f",fingerprint,"h",hash,"s",size,"c",count,"k",key,"r",remoteId,"e",expires)).update();return lock(id,tenant).orElseThrow();}
 public void markCompleted(UUID id){db.sql("update upload_sessions set status='COMPLETED',completed_at=now() where id=:id and status='READY'").param("id",id).update();}
 private Session map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{return new Session(rs.getObject("id",UUID.class),rs.getString("operation_id"),rs.getString("request_fingerprint"),rs.getString("content_hash"),rs.getLong("size_bytes"),rs.getInt("part_count"),rs.getString("object_key"),rs.getString("remote_upload_id"),rs.getString("status"),rs.getString("verification_status"),rs.getTimestamp("expires_at").toInstant());}
 public record Session(UUID id,String operationId,String fingerprint,String hash,long sizeBytes,int partCount,String objectKey,String remoteUploadId,String status,String verificationStatus,Instant expiresAt){}
}
