create table company_partnership_notifications (
  id uuid primary key default gen_random_uuid(),
  company_partnership_id uuid,
  recipient_id uuid not null,
  actor_user_id uuid not null,
  requester_company_id uuid not null,
  requester_company_name varchar(150) not null,
  target_company_id uuid not null,
  target_company_name varchar(150) not null,
  type varchar(20) not null,
  hidden boolean not null default false,
  created_at timestamptz not null default now(),
  constraint fk_company_partnership_notifications_recipient
    foreign key (recipient_id) references users (id) on delete cascade,
  constraint fk_company_partnership_notifications_actor_user
    foreign key (actor_user_id) references users (id) on delete cascade
);

create index idx_company_partnership_notifications_recipient_created_at
  on company_partnership_notifications (recipient_id, created_at desc);

create index idx_company_partnership_notifications_partnership
  on company_partnership_notifications (company_partnership_id);
