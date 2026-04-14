alter table tickets
  add column pending_transfer_to uuid,
  add column pending_transfer_requested_by uuid,
  add column pending_transfer_requested_at timestamptz;

alter table tickets
  add constraint fk_tickets_pending_transfer_to
    foreign key (pending_transfer_to) references users (id) on delete set null;

alter table tickets
  add constraint fk_tickets_pending_transfer_requested_by
    foreign key (pending_transfer_requested_by) references users (id) on delete set null;

create table ticket_transfer_notifications (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null,
  sender_id uuid not null,
  recipient_id uuid not null,
  status varchar(20) not null default 'PENDING',
  hidden boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint fk_ticket_transfer_notifications_ticket foreign key (ticket_id) references tickets (id) on delete cascade,
  constraint fk_ticket_transfer_notifications_sender foreign key (sender_id) references users (id) on delete cascade,
  constraint fk_ticket_transfer_notifications_recipient foreign key (recipient_id) references users (id) on delete cascade
);

create index idx_ticket_transfer_notifications_recipient_created_at
  on ticket_transfer_notifications (recipient_id, created_at desc);
