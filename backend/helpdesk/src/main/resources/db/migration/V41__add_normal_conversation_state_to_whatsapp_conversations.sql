alter table whatsapp_conversations
	add column if not exists normal_conversation_active boolean not null default false;

update whatsapp_conversations
set normal_conversation_active = true
where current_step = 'NORMAL_CONVERSATION_ACTIVE';
