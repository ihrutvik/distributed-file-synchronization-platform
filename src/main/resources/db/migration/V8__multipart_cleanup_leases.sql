alter table upload_sessions drop constraint upload_sessions_status_check;
alter table upload_sessions
 add constraint upload_sessions_status_check check(status in ('READY','COMPLETED','ABORTING','ABORTED')),
 add column cleanup_owner varchar(120),
 add column cleanup_leased_until timestamptz,
 add column abort_attempts integer not null default 0 check(abort_attempts >= 0),
 add column last_abort_error varchar(1000),
 add column aborted_at timestamptz;

create index idx_upload_sessions_cleanup
 on upload_sessions(expires_at,cleanup_leased_until)
 where status in ('READY','ABORTING');
