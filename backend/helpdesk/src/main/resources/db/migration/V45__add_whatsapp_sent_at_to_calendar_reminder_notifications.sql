alter table if exists public.calendar_reminder_notifications
  add column if not exists whatsapp_sent_at timestamptz;

do $$
declare
  company_record record;
begin
  for company_record in
    select schema_name
    from public.companies
    where schema_name is not null
      and schema_name <> ''
  loop
    execute format(
      'alter table if exists %I.calendar_reminder_notifications add column if not exists whatsapp_sent_at timestamptz',
      company_record.schema_name
    );
  end loop;
end $$;
