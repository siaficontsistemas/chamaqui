do $$
declare
  company_record record;
begin
  for company_record in
    select id, owner_user_id, company_name, schema_name
    from companies
  loop
    execute format('create table if not exists %I.sectors (like public.sectors including all)', company_record.schema_name);
    execute format('create table if not exists %I.sector_members (like public.sector_members including all)', company_record.schema_name);
    execute format('create table if not exists %I.team_invites (like public.team_invites including all)', company_record.schema_name);
    execute format('create table if not exists %I.team_invite_sectors (like public.team_invite_sectors including all)', company_record.schema_name);
    execute format('create table if not exists %I.tickets (like public.tickets including all)', company_record.schema_name);
    execute format('create table if not exists %I.ticket_messages (like public.ticket_messages including all)', company_record.schema_name);
    execute format('create table if not exists %I.ticket_attachments (like public.ticket_attachments including all)', company_record.schema_name);
    execute format('create table if not exists %I.ticket_status_history (like public.ticket_status_history including all)', company_record.schema_name);
    execute format('create table if not exists %I.ticket_assignment_notifications (like public.ticket_assignment_notifications including all)', company_record.schema_name);
    execute format('create table if not exists %I.ticket_transfer_notifications (like public.ticket_transfer_notifications including all)', company_record.schema_name);
    execute format('create table if not exists %I.ticket_closure_notifications (like public.ticket_closure_notifications including all)', company_record.schema_name);
    execute format('create table if not exists %I.team_membership_notifications (like public.team_membership_notifications including all)', company_record.schema_name);
    execute format('create table if not exists %I.calendar_obligations (like public.calendar_obligations including all)', company_record.schema_name);
    execute format('create table if not exists %I.calendar_obligation_recipients (like public.calendar_obligation_recipients including all)', company_record.schema_name);
    execute format('create table if not exists %I.calendar_reminder_notifications (like public.calendar_reminder_notifications including all)', company_record.schema_name);
    execute format('create table if not exists %I.whatsapp_conversations (like public.whatsapp_conversations including all)', company_record.schema_name);

    execute format($sql$
      insert into %I.sectors
      select *
      from public.sectors sector
      where sector.created_by = %L::uuid
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.sector_members
      select *
      from public.sector_members sector_member
      where sector_member.sector_id in (
        select sector.id
        from public.sectors sector
        where sector.created_by = %L::uuid
      )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.team_invites
      select *
      from public.team_invites invite
      where invite.invited_by = %L::uuid
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.team_invite_sectors
      select *
      from public.team_invite_sectors invite_sector
      where invite_sector.invite_id in (
        select invite.id
        from public.team_invites invite
        where invite.invited_by = %L::uuid
      )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.tickets
      select *
      from public.tickets ticket
      where ticket.sector_id in (
        select sector.id
        from public.sectors sector
        where sector.created_by = %L::uuid
      )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.ticket_messages
      select *
      from public.ticket_messages ticket_message
      where ticket_message.ticket_id in (
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

    execute format($sql$
      insert into %I.ticket_attachments
      select *
      from public.ticket_attachments attachment
      where attachment.ticket_id in (
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

    execute format($sql$
      insert into %I.ticket_status_history
      select *
      from public.ticket_status_history status_history
      where status_history.ticket_id in (
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

    execute format($sql$
      insert into %I.ticket_assignment_notifications
      select *
      from public.ticket_assignment_notifications notification
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

    execute format($sql$
      insert into %I.ticket_transfer_notifications
      select *
      from public.ticket_transfer_notifications notification
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

    execute format($sql$
      insert into %I.ticket_closure_notifications
      select *
      from public.ticket_closure_notifications notification
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

    execute format($sql$
      insert into %I.team_membership_notifications
      select *
      from public.team_membership_notifications notification
      where notification.sector_id in (
          select sector.id
          from public.sectors sector
          where sector.created_by = %L::uuid
        )
        or (
          notification.sector_id is null
          and (
            notification.removed_by = %L::uuid
            or coalesce(notification.company_name, '') = %L
          )
        )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id, company_record.owner_user_id, company_record.company_name);

    execute format($sql$
      insert into %I.calendar_obligations
      select *
      from public.calendar_obligations obligation
      where obligation.company_owner_id = %L::uuid
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.calendar_obligation_recipients
      select *
      from public.calendar_obligation_recipients obligation_recipient
      where obligation_recipient.obligation_id in (
        select obligation.id
        from public.calendar_obligations obligation
        where obligation.company_owner_id = %L::uuid
      )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.calendar_reminder_notifications
      select *
      from public.calendar_reminder_notifications notification
      where notification.obligation_id in (
        select obligation.id
        from public.calendar_obligations obligation
        where obligation.company_owner_id = %L::uuid
      )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id);

    execute format($sql$
      insert into %I.whatsapp_conversations
      select *
      from public.whatsapp_conversations conversation
      where conversation.company_owner_id = %L::uuid
         or conversation.sector_id in (
           select sector.id
           from public.sectors sector
           where sector.created_by = %L::uuid
         )
         or conversation.active_ticket_id in (
           select ticket.id
           from public.tickets ticket
           where ticket.sector_id in (
             select sector.id
             from public.sectors sector
             where sector.created_by = %L::uuid
           )
         )
      on conflict do nothing
    $sql$, company_record.schema_name, company_record.owner_user_id, company_record.owner_user_id, company_record.owner_user_id);
  end loop;
end $$;
