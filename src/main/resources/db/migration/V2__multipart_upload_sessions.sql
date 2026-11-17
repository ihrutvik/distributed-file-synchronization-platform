create table upload_sessions (
 id uuid primary key, tenant_id varchar(80) not null, operation_id varchar(120) not null,
 request_fingerprint char(64) not null, content_hash char(64) not null, size_bytes bigint not null check(size_bytes > 0),
 part_count integer not null check(part_count between 1 and 10000), object_key varchar(1024) not null,
 remote_upload_id text not null, status varchar(20) not null check(status in ('READY','COMPLETED','ABORTED')),
 created_at timestamptz not null, expires_at timestamptz not null, completed_at timestamptz,
 unique(tenant_id,operation_id)
);
create index idx_upload_sessions_expiry on upload_sessions(expires_at) where status='READY';
