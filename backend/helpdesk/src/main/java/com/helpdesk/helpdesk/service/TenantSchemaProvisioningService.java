package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantSchemaProvisioningService {

	private static final List<String> TENANT_TABLES = List.of(
		"sectors",
		"sector_members",
		"team_invites",
		"team_invite_sectors",
		"tickets",
		"ticket_messages",
		"ticket_attachments",
		"ticket_status_history",
		"ticket_assignment_notifications",
		"ticket_transfer_notifications",
		"ticket_closure_notifications",
		"ticket_reply_notifications",
		"team_membership_notifications",
		"calendar_obligations",
		"calendar_obligation_recipients",
		"calendar_reminder_notifications",
		"whatsapp_conversations"
	);

	private final JdbcTemplate jdbcTemplate;

	public TenantSchemaProvisioningService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void ensureTenantSchemaStructure(String schemaName) {
		String qualifiedSchema = quoteIdentifier(schemaName);
		for (String tableName : TENANT_TABLES) {
			String qualifiedTableName = qualifiedSchema + "." + quoteIdentifier(tableName);
			String publicTableName = "public." + quoteIdentifier(tableName);
			jdbcTemplate.execute(
				"create table if not exists " + qualifiedTableName + " (like " + publicTableName + " including all)"
			);
		}
	}

	private String quoteIdentifier(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
