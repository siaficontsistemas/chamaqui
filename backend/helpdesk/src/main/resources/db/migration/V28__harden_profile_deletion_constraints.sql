alter table whatsapp_conversations
  drop constraint if exists fk_whatsapp_conversations_company_owner;

alter table whatsapp_conversations
  add constraint fk_whatsapp_conversations_company_owner
    foreign key (company_owner_id) references users (id) on delete cascade;

alter table whatsapp_conversations
  drop constraint if exists fk_whatsapp_conversations_sector;

alter table whatsapp_conversations
  add constraint fk_whatsapp_conversations_sector
    foreign key (sector_id) references sectors (id) on delete set null;

alter table whatsapp_conversations
  drop constraint if exists fk_whatsapp_conversations_active_ticket;

alter table whatsapp_conversations
  add constraint fk_whatsapp_conversations_active_ticket
    foreign key (active_ticket_id) references tickets (id) on delete set null;

alter table users
  drop constraint if exists fk_users_company_owner;

alter table users
  add constraint fk_users_company_owner
    foreign key (company_owner_id) references users (id) on delete set null;
