create table calendar_reminder_notifications (
  id uuid primary key default gen_random_uuid(),
  obligation_id uuid not null,
  recipient_id uuid not null,
  hidden boolean not null default false,
  created_at timestamptz not null default now(),
  constraint uk_calendar_reminder_notifications_obligation_recipient
    unique (obligation_id, recipient_id),
  constraint fk_calendar_reminder_notifications_obligation
    foreign key (obligation_id) references calendar_obligations (id) on delete cascade,
  constraint fk_calendar_reminder_notifications_recipient
    foreign key (recipient_id) references users (id) on delete cascade
);

create index idx_calendar_reminder_notifications_recipient_created_at
  on calendar_reminder_notifications (recipient_id, created_at desc);
