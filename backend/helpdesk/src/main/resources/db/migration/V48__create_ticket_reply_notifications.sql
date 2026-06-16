create table ticket_reply_notifications (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null,
  message_id uuid not null,
  recipient_id uuid not null,
  hidden boolean not null default false,
  created_at timestamptz not null default now(),
  constraint uk_ticket_reply_notifications_message_recipient unique (message_id, recipient_id),
  constraint fk_ticket_reply_notifications_ticket foreign key (ticket_id) references tickets (id) on delete cascade,
  constraint fk_ticket_reply_notifications_message foreign key (message_id) references ticket_messages (id) on delete cascade,
  constraint fk_ticket_reply_notifications_recipient foreign key (recipient_id) references users (id) on delete cascade
);

create index idx_ticket_reply_notifications_recipient_created_at
  on ticket_reply_notifications (recipient_id, created_at desc);
