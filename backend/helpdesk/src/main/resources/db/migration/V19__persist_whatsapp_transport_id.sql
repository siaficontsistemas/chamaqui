alter table users
  add column if not exists whatsapp_transport_id varchar(80);

alter table whatsapp_conversations
  add column if not exists whatsapp_transport_id varchar(80);

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'uk_whatsapp_conversations_transport_id'
  ) then
    alter table whatsapp_conversations
      add constraint uk_whatsapp_conversations_transport_id unique (whatsapp_transport_id);
  end if;
end $$;

create index if not exists idx_users_whatsapp_transport_id
  on users (whatsapp_transport_id);
