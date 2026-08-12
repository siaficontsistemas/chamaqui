alter table tickets
  add column if not exists whatsapp_phone_number varchar(30),
  add column if not exists whatsapp_transport_id varchar(80);

do $$
declare
  company_record record;
begin
  for company_record in
    select schema_name
    from public.companies
  loop
    execute format(
      'alter table if exists %I.tickets add column if not exists whatsapp_phone_number varchar(30), add column if not exists whatsapp_transport_id varchar(80)',
      company_record.schema_name
    );
  end loop;
end
$$;
