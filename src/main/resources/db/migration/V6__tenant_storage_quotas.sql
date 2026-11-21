create table tenant_storage_quotas (
 tenant_id varchar(80) primary key,
 used_bytes bigint not null check (used_bytes >= 0),
 quota_bytes bigint not null check (quota_bytes > 0),
 updated_at timestamptz not null default now()
);

insert into tenant_storage_quotas(tenant_id,used_bytes,quota_bytes)
select tenant_id,coalesce(sum(size_bytes),0),10737418240
from file_heads where deleted=false group by tenant_id;
