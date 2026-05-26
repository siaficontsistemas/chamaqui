create table if not exists platform_admin_users (
  id uuid primary key default gen_random_uuid(),
  full_name varchar(150) not null,
  email citext not null,
  password_hash varchar(255) not null,
  active boolean not null default true,
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table platform_admin_users
  drop constraint if exists uk_platform_admin_users_email;

alter table platform_admin_users
  add constraint uk_platform_admin_users_email unique (email);

create index if not exists idx_platform_admin_users_active
  on platform_admin_users (active);
