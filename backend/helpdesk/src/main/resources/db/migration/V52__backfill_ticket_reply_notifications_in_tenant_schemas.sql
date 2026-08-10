do $$
declare
  company_record record;
begin
  for company_record in
    select owner_user_id, schema_name
    from companies
  loop
    execute format(
      'create table if not exists %I.ticket_reply_notifications (like public.ticket_reply_notifications including all)',
      company_record.schema_name
    );

    execute format($sql$
      insert into %I.ticket_reply_notifications
      select notification.*
      from public.ticket_reply_notifications notification
      where notification.ticket_id in (
        select ticket.id
        from public.tickets ticket
        where ticket.sector_id in (
          select sector.id
          from public.sectors sector
          where sector.created_by = %L::uuid
        )
      )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);
  end loop;
end
$$;
