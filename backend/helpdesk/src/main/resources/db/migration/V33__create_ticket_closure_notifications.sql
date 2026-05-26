create table if not exists ticket_closure_notifications (
	id uuid primary key,
	ticket_id uuid not null references tickets(id) on delete cascade,
	recipient_id uuid not null references users(id) on delete cascade,
	closed_by_id uuid not null references users(id) on delete cascade,
	hidden boolean not null default false,
	created_at timestamptz not null default now()
);

create index if not exists idx_ticket_closure_notifications_recipient
	on ticket_closure_notifications(recipient_id, hidden, created_at desc);

create index if not exists idx_ticket_closure_notifications_ticket
	on ticket_closure_notifications(ticket_id);
