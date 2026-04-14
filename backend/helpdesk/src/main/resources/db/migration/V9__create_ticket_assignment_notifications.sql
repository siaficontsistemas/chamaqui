create table ticket_assignment_notifications (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null,
  recipient_id uuid not null,
  hidden boolean not null default false,
  created_at timestamptz not null default now(),
  constraint uk_ticket_assignment_notifications_ticket_recipient unique (ticket_id, recipient_id),
  constraint fk_ticket_assignment_notifications_ticket foreign key (ticket_id) references tickets (id) on delete cascade,
  constraint fk_ticket_assignment_notifications_recipient foreign key (recipient_id) references users (id) on delete cascade
);

create index idx_ticket_assignment_notifications_recipient_created_at
  on ticket_assignment_notifications (recipient_id, created_at desc);
