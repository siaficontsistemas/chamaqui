alter table users
  drop constraint if exists uk_users_email;

drop index if exists uk_users_email;

create index if not exists idx_users_email
  on users (email);
