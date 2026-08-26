alter table ticket_messages
  add column if not exists whatsapp_message_id varchar(120),
  add column if not exists whatsapp_remote_jid varchar(180);

do $$
declare
  company_record record;
begin
  for company_record in
    select schema_name
    from companies
  loop
    execute format('alter table %I.ticket_messages add column if not exists whatsapp_message_id varchar(120), add column if not exists whatsapp_remote_jid varchar(180)', company_record.schema_name);
  end loop;
end $$;
