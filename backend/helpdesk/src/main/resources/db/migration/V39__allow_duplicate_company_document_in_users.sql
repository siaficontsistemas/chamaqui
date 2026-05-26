alter table users
  drop constraint if exists uk_users_company_document;

drop index if exists uk_users_company_document;

create index if not exists idx_users_company_document
  on users (company_document);
