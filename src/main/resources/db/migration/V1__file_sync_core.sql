create table file_heads (
 tenant_id varchar(80) not null, path varchar(1024) not null, file_id uuid not null,
 version bigint not null check(version > 0), content_hash char(64) not null, size_bytes bigint not null check(size_bytes >= 0),
 deleted boolean not null default false, committed_at timestamptz not null, primary key(tenant_id,path)
);
create table file_versions (
 file_id uuid not null, tenant_id varchar(80) not null, version bigint not null, path varchar(1024) not null,
 content_hash char(64) not null, size_bytes bigint not null check(size_bytes >= 0), device_id uuid not null,
 operation_id varchar(120) not null, request_fingerprint char(64) not null, deleted boolean not null default false,
 committed_at timestamptz not null, primary key(file_id,version), unique(tenant_id,operation_id)
);
create index idx_file_versions_tenant_path on file_versions(tenant_id,path,version desc);
create table outbox_events (
 id uuid primary key, tenant_id varchar(80) not null, aggregate_id uuid not null, event_type varchar(80) not null,
 payload jsonb not null, created_at timestamptz not null, published_at timestamptz, leased_until timestamptz, lease_owner varchar(120)
);
create index idx_outbox_unpublished on outbox_events(created_at) where published_at is null;
