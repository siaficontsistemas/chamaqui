alter table calendar_obligations
  add column recipient_id uuid;

update calendar_obligations
set recipient_id = created_by
where recipient_id is null;

alter table calendar_obligations
  alter column recipient_id set not null;

alter table calendar_obligations
  add constraint fk_calendar_obligations_recipient
    foreign key (recipient_id) references users (id) on delete cascade;

create index idx_calendar_obligations_recipient_due_at
  on calendar_obligations (recipient_id, due_at asc);
