do $$
declare
  schema_record record;
begin
  for schema_record in
    select nspname as schema_name
    from pg_namespace
    where nspname = 'public' or nspname like 'tenant_%'
  loop
    execute format(
      'alter table if exists %I.calendar_obligations add column if not exists priority_code varchar(20)',
      schema_record.schema_name
    );
    execute format(
      'update %I.calendar_obligations set priority_code = ''MEDIUM'' where priority_code is null',
      schema_record.schema_name
    );
    execute format(
      'alter table if exists %I.calendar_obligations alter column priority_code set default ''MEDIUM''',
      schema_record.schema_name
    );
  end loop;
end $$;
