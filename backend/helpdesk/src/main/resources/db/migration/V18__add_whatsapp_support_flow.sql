alter table users
  add column if not exists is_simplified boolean not null default false;

alter table tickets
  add column if not exists channel varchar(20) not null default 'PORTAL';

update tickets
set channel = 'PORTAL'
where channel is null;

create table if not exists whatsapp_conversations (
  id uuid primary key,
  phone_number varchar(30) not null,
  current_step varchar(40) not null,
  full_name varchar(150),
  email varchar(150),
  document_number varchar(20),
  description text,
  company_owner_id uuid,
  sector_id uuid,
  active_ticket_id uuid,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'uk_whatsapp_conversations_phone_number'
  ) then
    alter table whatsapp_conversations
      add constraint uk_whatsapp_conversations_phone_number unique (phone_number);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'fk_whatsapp_conversations_company_owner'
  ) then
    alter table whatsapp_conversations
      add constraint fk_whatsapp_conversations_company_owner
      foreign key (company_owner_id) references users (id);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'fk_whatsapp_conversations_sector'
  ) then
    alter table whatsapp_conversations
      add constraint fk_whatsapp_conversations_sector
      foreign key (sector_id) references sectors (id);
  end if;
end $$;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'fk_whatsapp_conversations_active_ticket'
  ) then
    alter table whatsapp_conversations
      add constraint fk_whatsapp_conversations_active_ticket
      foreign key (active_ticket_id) references tickets (id);
  end if;
end $$;

create index if not exists idx_whatsapp_conversations_company_owner_id
  on whatsapp_conversations (company_owner_id);

create index if not exists idx_whatsapp_conversations_sector_id
  on whatsapp_conversations (sector_id);

create index if not exists idx_whatsapp_conversations_active_ticket_id
  on whatsapp_conversations (active_ticket_id);
