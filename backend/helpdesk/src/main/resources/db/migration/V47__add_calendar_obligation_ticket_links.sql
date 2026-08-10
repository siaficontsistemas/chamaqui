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
      'create table if not exists %I.calendar_obligation_tickets (
        obligation_id uuid not null,
        ticket_id uuid not null,
        primary key (obligation_id, ticket_id),
        constraint fk_calendar_obligation_tickets_obligation
          foreign key (obligation_id) references %I.calendar_obligations (id) on delete cascade,
        constraint fk_calendar_obligation_tickets_ticket
          foreign key (ticket_id) references %I.tickets (id) on delete cascade
      )',
      company_record.schema_name,
      company_record.schema_name,
      company_record.schema_name
    );

    execute format(
      'create index if not exists idx_calendar_obligation_tickets_ticket on %I.calendar_obligation_tickets (ticket_id)',
      company_record.schema_name
    );
  end loop;
end $$;
