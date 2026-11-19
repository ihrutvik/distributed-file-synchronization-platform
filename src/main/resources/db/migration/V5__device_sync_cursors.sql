create table device_sync_cursors (
 tenant_id varchar(80) not null,
 device_id uuid not null,
 acknowledged_cursor bigint not null check (acknowledged_cursor >= 0),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 last_seen_at timestamptz not null default now(),
 primary key (tenant_id, device_id)
);
create index idx_device_sync_cursors_active_watermark
 on device_sync_cursors(tenant_id, last_seen_at, acknowledged_cursor);

