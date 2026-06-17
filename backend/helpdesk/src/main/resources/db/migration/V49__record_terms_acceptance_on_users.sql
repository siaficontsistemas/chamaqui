alter table users
  add column if not exists terms_accepted_at timestamptz,
  add column if not exists terms_version varchar(40);
