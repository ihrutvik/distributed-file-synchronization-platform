alter table upload_sessions
 add column blob_health varchar(20) not null default 'HEALTHY' check (blob_health in ('HEALTHY','MISSING')),
 add column blob_health_reason varchar(500),
 add column reconciled_at timestamptz,
 add column reconciliation_lease_owner varchar(120),
 add column reconciliation_lease_until timestamptz;

create index idx_upload_sessions_reconciliation
 on upload_sessions(reconciled_at,reconciliation_lease_until)
 where status='COMPLETED' and verification_status='VERIFIED';
