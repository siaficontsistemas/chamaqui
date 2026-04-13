create extension if not exists pgcrypto;
create extension if not exists citext;

create function set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create table roles (
  id uuid primary key default gen_random_uuid(),
  code varchar(40) not null,
  name varchar(80) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uk_roles_code unique (code),
  constraint uk_roles_name unique (name)
);

create table users (
  id uuid primary key default gen_random_uuid(),
  full_name varchar(150) not null,
  email citext not null,
  password_hash varchar(255) not null,
  status varchar(20) not null default 'PENDING',
  is_email_verified boolean not null default false,
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint uk_users_email unique (email),
  constraint ck_users_full_name_length check (char_length(btrim(full_name)) >= 3),
  constraint ck_users_status check (status in ('PENDING', 'ACTIVE', 'INACTIVE', 'LOCKED'))
);

create table user_roles (
  user_id uuid not null,
  role_id uuid not null,
  created_at timestamptz not null default now(),
  primary key (user_id, role_id),
  constraint fk_user_roles_user foreign key (user_id) references users (id) on delete cascade,
  constraint fk_user_roles_role foreign key (role_id) references roles (id) on delete restrict
);

create table sectors (
  id uuid primary key default gen_random_uuid(),
  name varchar(120) not null,
  slug varchar(140) not null,
  description varchar(255),
  created_by uuid not null,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  archived_at timestamptz,
  constraint uk_sectors_slug unique (slug),
  constraint fk_sectors_created_by foreign key (created_by) references users (id) on delete restrict,
  constraint ck_sectors_name_length check (char_length(btrim(name)) >= 2),
  constraint ck_sectors_slug_format check (slug = lower(slug) and slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

create table sector_members (
  id uuid primary key default gen_random_uuid(),
  sector_id uuid not null,
  user_id uuid not null,
  assigned_by uuid not null,
  assigned_at timestamptz not null default now(),
  constraint uk_sector_members_sector_user unique (sector_id, user_id),
  constraint fk_sector_members_sector foreign key (sector_id) references sectors (id) on delete cascade,
  constraint fk_sector_members_user foreign key (user_id) references users (id) on delete cascade,
  constraint fk_sector_members_assigned_by foreign key (assigned_by) references users (id) on delete restrict
);

create table team_invites (
  id uuid primary key default gen_random_uuid(),
  email citext not null,
  invited_name varchar(150) not null,
  invited_by uuid not null,
  accepted_user_id uuid,
  token_hash varchar(255) not null,
  status varchar(20) not null default 'PENDING',
  expires_at timestamptz not null,
  accepted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uk_team_invites_token_hash unique (token_hash),
  constraint fk_team_invites_invited_by foreign key (invited_by) references users (id) on delete restrict,
  constraint fk_team_invites_accepted_user foreign key (accepted_user_id) references users (id) on delete set null,
  constraint ck_team_invites_name_length check (char_length(btrim(invited_name)) >= 3),
  constraint ck_team_invites_status check (status in ('PENDING', 'ACCEPTED', 'EXPIRED', 'CANCELED')),
  constraint ck_team_invites_expiration check (expires_at > created_at),
  constraint ck_team_invites_acceptance check (
    (status = 'ACCEPTED' and accepted_at is not null)
    or (status <> 'ACCEPTED' and accepted_at is null)
  )
);

create table team_invite_sectors (
  invite_id uuid not null,
  sector_id uuid not null,
  created_at timestamptz not null default now(),
  primary key (invite_id, sector_id),
  constraint fk_team_invite_sectors_invite foreign key (invite_id) references team_invites (id) on delete cascade,
  constraint fk_team_invite_sectors_sector foreign key (sector_id) references sectors (id) on delete cascade
);

create table ticket_statuses (
  id uuid primary key default gen_random_uuid(),
  code varchar(40) not null,
  name varchar(80) not null,
  sort_order integer not null,
  is_terminal boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uk_ticket_statuses_code unique (code),
  constraint uk_ticket_statuses_name unique (name),
  constraint uk_ticket_statuses_sort_order unique (sort_order),
  constraint ck_ticket_statuses_sort_order check (sort_order > 0)
);

create table ticket_priorities (
  id uuid primary key default gen_random_uuid(),
  code varchar(40) not null,
  name varchar(80) not null,
  sort_order integer not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uk_ticket_priorities_code unique (code),
  constraint uk_ticket_priorities_name unique (name),
  constraint uk_ticket_priorities_sort_order unique (sort_order),
  constraint ck_ticket_priorities_sort_order check (sort_order > 0)
);

create table tickets (
  id uuid primary key default gen_random_uuid(),
  protocol varchar(30) not null,
  title varchar(180) not null,
  description text not null,
  requester_id uuid not null,
  assigned_to uuid,
  sector_id uuid not null,
  status_id uuid not null,
  priority_id uuid not null,
  opened_at timestamptz not null default now(),
  first_response_at timestamptz,
  resolved_at timestamptz,
  closed_at timestamptz,
  due_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint uk_tickets_protocol unique (protocol),
  constraint fk_tickets_requester foreign key (requester_id) references users (id) on delete restrict,
  constraint fk_tickets_assigned_to foreign key (assigned_to) references users (id) on delete set null,
  constraint fk_tickets_sector foreign key (sector_id) references sectors (id) on delete restrict,
  constraint fk_tickets_status foreign key (status_id) references ticket_statuses (id) on delete restrict,
  constraint fk_tickets_priority foreign key (priority_id) references ticket_priorities (id) on delete restrict,
  constraint ck_tickets_title_length check (char_length(btrim(title)) >= 3),
  constraint ck_tickets_description_length check (char_length(btrim(description)) >= 10),
  constraint ck_tickets_protocol_length check (char_length(btrim(protocol)) >= 6),
  constraint ck_tickets_first_response_at check (first_response_at is null or first_response_at >= opened_at),
  constraint ck_tickets_resolved_at check (resolved_at is null or resolved_at >= opened_at),
  constraint ck_tickets_closed_at check (closed_at is null or closed_at >= opened_at),
  constraint ck_tickets_due_at check (due_at is null or due_at >= opened_at)
);

create table ticket_messages (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null,
  author_id uuid not null,
  message text not null,
  is_internal boolean not null default false,
  created_at timestamptz not null default now(),
  constraint fk_ticket_messages_ticket foreign key (ticket_id) references tickets (id) on delete cascade,
  constraint fk_ticket_messages_author foreign key (author_id) references users (id) on delete restrict,
  constraint ck_ticket_messages_message_length check (char_length(btrim(message)) >= 1)
);

create table ticket_attachments (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null,
  uploaded_by uuid not null,
  original_file_name varchar(255) not null,
  storage_key varchar(255) not null,
  content_type varchar(120) not null,
  size_bytes bigint not null,
  created_at timestamptz not null default now(),
  constraint uk_ticket_attachments_storage_key unique (storage_key),
  constraint fk_ticket_attachments_ticket foreign key (ticket_id) references tickets (id) on delete cascade,
  constraint fk_ticket_attachments_uploaded_by foreign key (uploaded_by) references users (id) on delete restrict,
  constraint ck_ticket_attachments_size_bytes check (size_bytes > 0)
);

create table ticket_status_history (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null,
  changed_by uuid not null,
  old_status_id uuid,
  new_status_id uuid not null,
  note varchar(255),
  changed_at timestamptz not null default now(),
  constraint fk_ticket_status_history_ticket foreign key (ticket_id) references tickets (id) on delete cascade,
  constraint fk_ticket_status_history_changed_by foreign key (changed_by) references users (id) on delete restrict,
  constraint fk_ticket_status_history_old_status foreign key (old_status_id) references ticket_statuses (id) on delete restrict,
  constraint fk_ticket_status_history_new_status foreign key (new_status_id) references ticket_statuses (id) on delete restrict
);

create table audit_log (
  id uuid primary key default gen_random_uuid(),
  actor_user_id uuid,
  entity_name varchar(80) not null,
  entity_id uuid,
  action varchar(50) not null,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  constraint fk_audit_log_actor foreign key (actor_user_id) references users (id) on delete set null
);

create unique index uk_sectors_name_active on sectors ((lower(name))) where archived_at is null;
create index idx_users_status on users (status);
create index idx_sector_members_user_id on sector_members (user_id);
create index idx_sector_members_sector_id on sector_members (sector_id);
create index idx_team_invites_email_status on team_invites (email, status);
create index idx_team_invites_invited_by on team_invites (invited_by);
create index idx_tickets_requester_id on tickets (requester_id);
create index idx_tickets_assigned_to on tickets (assigned_to);
create index idx_tickets_sector_status on tickets (sector_id, status_id);
create index idx_tickets_status_priority on tickets (status_id, priority_id);
create index idx_tickets_created_at on tickets (created_at desc);
create index idx_ticket_messages_ticket_created_at on ticket_messages (ticket_id, created_at);
create index idx_ticket_attachments_ticket_id on ticket_attachments (ticket_id);
create index idx_ticket_status_history_ticket_changed_at on ticket_status_history (ticket_id, changed_at);
create index idx_audit_log_entity on audit_log (entity_name, entity_id);
create index idx_audit_log_actor_user_id on audit_log (actor_user_id);

create trigger trg_roles_set_updated_at
before update on roles
for each row
execute function set_updated_at();

create trigger trg_users_set_updated_at
before update on users
for each row
execute function set_updated_at();

create trigger trg_sectors_set_updated_at
before update on sectors
for each row
execute function set_updated_at();

create trigger trg_team_invites_set_updated_at
before update on team_invites
for each row
execute function set_updated_at();

create trigger trg_ticket_statuses_set_updated_at
before update on ticket_statuses
for each row
execute function set_updated_at();

create trigger trg_ticket_priorities_set_updated_at
before update on ticket_priorities
for each row
execute function set_updated_at();

create trigger trg_tickets_set_updated_at
before update on tickets
for each row
execute function set_updated_at();
