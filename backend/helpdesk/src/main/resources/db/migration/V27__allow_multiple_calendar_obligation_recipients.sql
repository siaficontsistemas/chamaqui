create table calendar_obligation_recipients (
  obligation_id uuid not null,
  recipient_id uuid not null,
  constraint pk_calendar_obligation_recipients
    primary key (obligation_id, recipient_id),
  constraint fk_calendar_obligation_recipients_obligation
    foreign key (obligation_id) references calendar_obligations (id) on delete cascade,
  constraint fk_calendar_obligation_recipients_recipient
    foreign key (recipient_id) references users (id) on delete cascade
);

insert into calendar_obligation_recipients (obligation_id, recipient_id)
select id, recipient_id
from calendar_obligations
where recipient_id is not null
on conflict do nothing;

create index idx_calendar_obligation_recipients_recipient
  on calendar_obligation_recipients (recipient_id);

drop index if exists idx_calendar_obligations_recipient_due_at;

alter table calendar_obligations
  drop constraint if exists fk_calendar_obligations_recipient;

alter table calendar_obligations
  drop column if exists recipient_id;
