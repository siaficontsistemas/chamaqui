create table calendar_obligations (
  id uuid primary key default gen_random_uuid(),
  company_owner_id uuid not null,
  created_by uuid not null,
  title varchar(180) not null,
  description varchar(2000),
  due_at timestamptz not null,
  reminder_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint fk_calendar_obligations_company_owner
    foreign key (company_owner_id) references users (id) on delete cascade,
  constraint fk_calendar_obligations_created_by
    foreign key (created_by) references users (id) on delete cascade
);

create index idx_calendar_obligations_company_owner_due_at
  on calendar_obligations (company_owner_id, due_at asc);

create index idx_calendar_obligations_company_owner_completed_at
  on calendar_obligations (company_owner_id, completed_at);
