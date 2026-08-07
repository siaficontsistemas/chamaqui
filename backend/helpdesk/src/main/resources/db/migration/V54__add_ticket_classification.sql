alter table tickets add column if not exists category varchar(30);
alter table tickets add column if not exists system_error_type varchar(20);

do $$
declare company_record record;
begin
  for company_record in select schema_name from public.companies where schema_name is not null and schema_name <> '' loop
    execute format('alter table if exists %I.tickets add column if not exists category varchar(30)', company_record.schema_name);
    execute format('alter table if exists %I.tickets add column if not exists system_error_type varchar(20)', company_record.schema_name);
  end loop;
end $$;
