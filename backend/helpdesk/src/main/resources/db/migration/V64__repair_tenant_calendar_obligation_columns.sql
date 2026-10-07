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
      'alter table if exists %I.calendar_obligations add column if not exists linked_company_owner_id uuid',
      company_record.schema_name
    );
    execute format(
      'alter table if exists %I.calendar_obligations add column if not exists priority_code varchar(20)',
      company_record.schema_name
    );
    execute format(
      'update %I.calendar_obligations set linked_company_owner_id = company_owner_id where linked_company_owner_id is null',
      company_record.schema_name
    );
    execute format(
      'update %I.calendar_obligations set priority_code = ''MEDIUM'' where priority_code is null or btrim(priority_code) = ''''',
      company_record.schema_name
    );
  end loop;
end $$;
