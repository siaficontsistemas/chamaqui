alter table ticket_messages
  add column if not exists reply_to_message_id uuid;

alter table ticket_messages
  drop constraint if exists fk_ticket_messages_reply_to;

alter table ticket_messages
  add constraint fk_ticket_messages_reply_to
  foreign key (reply_to_message_id) references ticket_messages (id) on delete set null;

create index if not exists idx_ticket_messages_reply_to_message_id
  on ticket_messages (reply_to_message_id);

do $$
declare
  company_record record;
begin
  for company_record in
    select schema_name
    from companies
  loop
    execute format('alter table %I.ticket_messages add column if not exists reply_to_message_id uuid', company_record.schema_name);
    execute format('alter table %I.ticket_messages drop constraint if exists fk_ticket_messages_reply_to', company_record.schema_name);
    execute format('alter table %I.ticket_messages add constraint fk_ticket_messages_reply_to foreign key (reply_to_message_id) references %I.ticket_messages (id) on delete set null', company_record.schema_name, company_record.schema_name);
    execute format('create index if not exists idx_ticket_messages_reply_to_message_id on %I.ticket_messages (reply_to_message_id)', company_record.schema_name);
  end loop;
end $$;
