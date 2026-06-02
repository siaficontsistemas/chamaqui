do $$
declare
	schema_record record;
begin
	for schema_record in
		select schema_name
		from information_schema.schemata
		where schema_name = 'public'
			or schema_name like 'tenant\_%' escape '\'
	loop
		if exists (
			select 1
			from information_schema.tables
			where table_schema = schema_record.schema_name
				and table_name = 'whatsapp_conversations'
		) then
			execute format(
				'alter table %I.whatsapp_conversations add column if not exists pending_resume_message text',
				schema_record.schema_name
			);
			execute format(
				'alter table %I.whatsapp_conversations add column if not exists pending_resume_attachments text',
				schema_record.schema_name
			);
			execute format(
				'alter table %I.whatsapp_conversations add column if not exists last_outbound_message_at timestamptz',
				schema_record.schema_name
			);
		end if;
	end loop;
end
$$;
