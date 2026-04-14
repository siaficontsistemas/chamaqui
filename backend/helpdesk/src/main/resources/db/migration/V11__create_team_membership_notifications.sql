create table team_membership_notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null,
  removed_by uuid not null,
  sector_id uuid,
  type varchar(30) not null,
  hidden boolean not null default false,
  created_at timestamptz not null default now(),
  constraint fk_team_membership_notifications_recipient
    foreign key (recipient_id) references users (id) on delete cascade,
  constraint fk_team_membership_notifications_removed_by
    foreign key (removed_by) references users (id) on delete cascade,
  constraint fk_team_membership_notifications_sector
    foreign key (sector_id) references sectors (id) on delete set null
);

create index idx_team_membership_notifications_recipient_created_at
  on team_membership_notifications (recipient_id, created_at desc);
