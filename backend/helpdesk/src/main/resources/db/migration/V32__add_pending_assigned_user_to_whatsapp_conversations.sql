alter table whatsapp_conversations
	add column if not exists pending_assigned_user_id uuid;
