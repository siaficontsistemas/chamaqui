alter table whatsapp_conversations
  add column if not exists pending_message text;

alter table whatsapp_conversations
  add column if not exists sector_id uuid;

do $$
begin
  if exists (
    select 1
    from information_schema.columns
    where table_name = 'whatsapp_conversations'
      and column_name = 'full_name'
  ) then
    alter table whatsapp_conversations
      drop column full_name;
  end if;

  if exists (
    select 1
    from information_schema.columns
    where table_name = 'whatsapp_conversations'
      and column_name = 'email'
  ) then
    alter table whatsapp_conversations
      drop column email;
  end if;

  if exists (
    select 1
    from information_schema.columns
    where table_name = 'whatsapp_conversations'
      and column_name = 'document_number'
  ) then
    alter table whatsapp_conversations
      drop column document_number;
  end if;

  if exists (
    select 1
    from information_schema.columns
    where table_name = 'whatsapp_conversations'
      and column_name = 'description'
  ) then
    alter table whatsapp_conversations
      drop column description;
  end if;
end $$;

do $$
begin
  if exists (
    select 1
    from pg_constraint
    where conname = 'uk_whatsapp_conversations_phone_number'
  ) then
    alter table whatsapp_conversations
      drop constraint uk_whatsapp_conversations_phone_number;
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'uk_whatsapp_conversations_company_phone'
  ) then
    alter table whatsapp_conversations
      add constraint uk_whatsapp_conversations_company_phone unique (company_owner_id, phone_number);
  end if;
end $$;

do $$
begin
  if exists (
    select 1
    from pg_constraint
    where conname = 'uk_whatsapp_conversations_transport_id'
  ) then
    alter table whatsapp_conversations
      drop constraint uk_whatsapp_conversations_transport_id;
  end if;

  if not exists (
    select 1
    from pg_constraint
    where conname = 'uk_whatsapp_conversations_company_transport'
  ) then
    alter table whatsapp_conversations
      add constraint uk_whatsapp_conversations_company_transport unique (company_owner_id, whatsapp_transport_id);
  end if;
end $$;

create index if not exists idx_whatsapp_conversations_company_owner_phone
  on whatsapp_conversations (company_owner_id, phone_number);

create index if not exists idx_whatsapp_conversations_company_owner_transport
  on whatsapp_conversations (company_owner_id, whatsapp_transport_id);
