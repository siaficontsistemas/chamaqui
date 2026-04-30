alter table whatsapp_conversations
  add column if not exists last_inbound_message_at timestamptz;

alter table whatsapp_conversations
  add column if not exists last_ticket_selection_prompt_at timestamptz;
