alter table users
  add column if not exists password_reset_token_hash varchar(120),
  add column if not exists password_reset_token_expires_at timestamptz;

create index if not exists idx_users_password_reset_token_hash
  on users (password_reset_token_hash)
  where password_reset_token_hash is not null;
