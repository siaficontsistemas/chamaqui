update public.users
set whatsapp_transport_id = null
where whatsapp_transport_id is not null
  and (
    lower(whatsapp_transport_id) like '%@lid'
    or whatsapp_transport_id in (
      select duplicated.whatsapp_transport_id
      from (
        select whatsapp_transport_id
        from public.users
        where deleted_at is null
          and whatsapp_transport_id is not null
          and btrim(whatsapp_transport_id) <> ''
        group by whatsapp_transport_id
        having count(*) > 1
      ) duplicated
    )
  );

do $$
declare
  company_record record;
begin
  for company_record in
    select schema_name
    from companies
  loop
    if exists (
      select 1
      from information_schema.tables
      where table_schema = company_record.schema_name
        and table_name = 'users'
    ) then
      execute format($sql$
        update %I.users
        set whatsapp_transport_id = null
        where whatsapp_transport_id is not null
          and (
            lower(whatsapp_transport_id) like '%%@lid'
            or whatsapp_transport_id in (
              select duplicated.whatsapp_transport_id
              from (
                select whatsapp_transport_id
                from %I.users
                where deleted_at is null
                  and whatsapp_transport_id is not null
                  and btrim(whatsapp_transport_id) <> ''
                group by whatsapp_transport_id
                having count(*) > 1
              ) duplicated
            )
          )
      $sql$, company_record.schema_name, company_record.schema_name);
    end if;
  end loop;
end
$$;
