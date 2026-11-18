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

