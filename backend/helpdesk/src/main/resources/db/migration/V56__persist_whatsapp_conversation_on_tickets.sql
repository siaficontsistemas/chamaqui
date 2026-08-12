alter table tickets
  add column if not exists whatsapp_conversation_id uuid;

do $$
declare
  company_record record;
begin
  for company_record in
    select schema_name
    from public.companies
  loop
    execute format(
      'alter table if exists %I.tickets add column if not exists whatsapp_conversation_id uuid',
      company_record.schema_name
    );
  end loop;
end
$$;
