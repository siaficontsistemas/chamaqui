do $$
declare
	schema_record record;
begin
	for schema_record in
		select schema_name
		from information_schema.schemata
		where schema_name like 'tenant\_%' escape '\'
	loop
		if exists (
			select 1
			from information_schema.tables
			where table_schema = schema_record.schema_name
				and table_name = 'whatsapp_conversations'
		) then
			execute format(
				'alter table %I.whatsapp_conversations add column if not exists normal_conversation_active boolean not null default false',
				schema_record.schema_name
			);

			execute format(
				'update %I.whatsapp_conversations set normal_conversation_active = true where current_step = %L',
				schema_record.schema_name,
				'NORMAL_CONVERSATION_ACTIVE'
			);
		end if;
	end loop;
end
$$;
