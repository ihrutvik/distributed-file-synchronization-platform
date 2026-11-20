# Architecture decisions

## Initial boundary

The control plane records immutable file versions and durable change events. File bytes live in S3-compatible object storage under their SHA-256 content hash; PostgreSQL owns namespace, version, conflict, and tombstone correctness. Kafka distributes committed changes to other devices and indexing consumers.

## Decisions

1. **Content-addressed blobs.** A SHA-256 key deduplicates identical bytes and makes integrity verifiable. The database never stores large file bodies.
2. **Compare-and-set head updates.** Every update names the version it was based on. A stale base produces an explicit `409 VERSION_CONFLICT`; the server never silently overwrites another device's work.
3. **Explicit conflict preservation.** `REJECT` remains the safe default. With `KEEP_BOTH`, a stale edit becomes a deterministic sibling file instead of overwriting the head.
4. **Immutable version history.** Each accepted mutation appends a version, then advances the locked path head in the same transaction.
5. **Durable tombstones.** Deletes are versions, not row removal, preventing offline devices from resurrecting old content when they reconnect.
6. **Payload-aware operations.** `(tenant, operationId)` is unique. Exact retries return the original result, while changed reuse conflicts.
7. **Transactional outbox.** The version and its `FILE_VERSION_COMMITTED` or `FILE_DELETED` event commit atomically. Kafka publication can retry independently.
8. **Tenant-local namespaces.** Paths are unique within a tenant, normalized before persistence, and cannot traverse parents.

## Concurrent offline-edit policy

The version API accepts an optional `conflictPolicy`. `REJECT` preserves the original compare-and-set behavior and its historical request fingerprint, so exact retries created before this feature remain compatible. `KEEP_BOTH` is appropriate when a client cannot merge content locally and preserving both byte streams is safer than asking the user to repeat the upload.

For a stale base, the service keeps the existing path head untouched and derives a sibling name from the device ID plus a SHA-256 token of `(deviceId, operationId)`. The name is deterministic, retains the original extension, and is truncated safely to the 1,024-character namespace limit. The conflict copy starts an independent immutable version history at version 1 and publishes through the same transactional outbox. The operation fingerprint includes the non-default policy, preventing the same idempotency key from being reused with different conflict semantics.

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

## Durable device progress and compaction safety

A client acknowledges a cursor only after the corresponding page is durably applied to its local state. The server stores one row per `(tenant, device)` and serializes updates with a row lock. Acknowledgements may stay equal for harmless retries but can never move backwards; a cursor beyond the current tenant feed head is rejected because it could make later retention unsafe.

The first concurrent acknowledgement uses an atomic PostgreSQL upsert, keeping the greater cursor if two initial requests race. Every successful acknowledgement refreshes `last_seen_at`. The compaction-watermark endpoint calculates the minimum cursor among devices active inside an explicit window. History at or below that cursor is eligible for a future compactor, but this checkpoint intentionally reports the watermark without deleting data. The activity cutoff is a product policy: a longer window protects rarely connected devices at the cost of retaining more history.

## Evolution plan

Upcoming checkpoints add directory moves, quotas, Redis hot manifests, garbage collection, S3 event reconciliation, security, tracing, load tests, AWS deployment, and SLO dashboards.

