alter table whatsapp_conversations
  add column if not exists pending_name varchar(150);

alter table whatsapp_conversations
  add column if not exists pending_email varchar(150);

alter table whatsapp_conversations
  add column if not exists pending_document varchar(20);

alter table whatsapp_conversations
  add column if not exists pending_subject varchar(180);
