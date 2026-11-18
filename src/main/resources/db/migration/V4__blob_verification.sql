alter table upload_sessions add column verification_status varchar(20) not null default 'PENDING' check (verification_status in ('PENDING','VERIFYING','VERIFIED','REJECTED'));
alter table upload_sessions add column verification_lease_owner varchar(100);
alter table upload_sessions add column verification_lease_until timestamptz;
alter table upload_sessions add column verified_at timestamptz;
alter table upload_sessions add column verification_failure varchar(500);
create index idx_upload_sessions_verification on upload_sessions(completed_at)
 where status='COMPLETED' and verification_status in ('PENDING','VERIFYING');

