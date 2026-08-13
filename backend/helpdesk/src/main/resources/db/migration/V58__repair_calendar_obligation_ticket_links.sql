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
      'create table if not exists %I.calendar_obligation_tickets (
        obligation_id uuid not null,
        ticket_id uuid not null,
        primary key (obligation_id, ticket_id),
        constraint fk_calendar_obligation_tickets_obligation
          foreign key (obligation_id) references %I.calendar_obligations (id) on delete cascade,
        constraint fk_calendar_obligation_tickets_ticket
          foreign key (ticket_id) references %I.tickets (id) on delete cascade
      )',
      schema_record.schema_name,
      schema_record.schema_name,
      schema_record.schema_name
    );

    execute format(
      'create index if not exists idx_calendar_obligation_tickets_ticket on %I.calendar_obligation_tickets (ticket_id)',
      schema_record.schema_name
    );
  end loop;
end $$;
