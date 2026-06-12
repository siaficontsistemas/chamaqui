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
      $sql$update %I.calendar_obligations
        set priority_code = 'MEDIUM'
      where priority_code is null or btrim(priority_code) = ''$sql$,
      company_record.schema_name
    );

    execute format(
      'alter table if exists %I.calendar_obligations alter column linked_company_owner_id set not null',
      company_record.schema_name
    );

    execute format(
      'alter table if exists %I.calendar_obligations alter column priority_code set not null',
      company_record.schema_name
    );

    execute format(
      'alter table if exists %I.calendar_obligations drop constraint if exists fk_calendar_obligations_linked_company_owner',
      company_record.schema_name
    );

    execute format(
      'alter table if exists %I.calendar_obligations add constraint fk_calendar_obligations_linked_company_owner foreign key (linked_company_owner_id) references users (id) on delete cascade',
      company_record.schema_name
    );

    execute format(
      'create index if not exists idx_calendar_obligations_linked_company_owner on %I.calendar_obligations (linked_company_owner_id)',
      company_record.schema_name
    );
  end loop;
end $$;
