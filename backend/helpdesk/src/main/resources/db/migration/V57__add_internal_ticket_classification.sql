alter table tickets
  add column if not exists internal_type varchar(40),
  add column if not exists internal_system_area varchar(40);

do $$
declare
  company_record record;
begin
  for company_record in select schema_name from companies loop
    execute format(
      'alter table if exists %I.tickets add column if not exists internal_type varchar(40), add column if not exists internal_system_area varchar(40)',
      company_record.schema_name
    );
  end loop;
end $$;
