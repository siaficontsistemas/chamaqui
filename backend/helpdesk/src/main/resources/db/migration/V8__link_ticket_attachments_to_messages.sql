alter table ticket_attachments
  add column message_id uuid;

alter table ticket_attachments
  add constraint fk_ticket_attachments_message
  foreign key (message_id) references ticket_messages (id) on delete cascade;

create index idx_ticket_attachments_message_id on ticket_attachments (message_id);
