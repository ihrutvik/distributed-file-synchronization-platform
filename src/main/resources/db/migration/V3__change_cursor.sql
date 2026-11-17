alter table file_versions add column change_sequence bigint generated always as identity;
create unique index uq_file_versions_change_sequence on file_versions(change_sequence);
create index idx_file_versions_tenant_changes on file_versions(tenant_id,change_sequence);
