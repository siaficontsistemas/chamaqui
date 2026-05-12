alter table users
  drop constraint if exists uk_users_document_number;

create index if not exists idx_users_document_number
  on users (document_number);
