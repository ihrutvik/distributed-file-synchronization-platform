alter table file_versions
 add column change_type varchar(20) not null default 'UPSERT',
 add column previous_path varchar(1024),
 add constraint chk_file_versions_change_type check (change_type in ('UPSERT','DELETE','MOVE')),
 add constraint chk_move_previous_path check (
  (change_type = 'MOVE' and previous_path is not null) or
  (change_type <> 'MOVE' and previous_path is null)
 );

update file_versions set change_type = 'DELETE' where deleted = true;
