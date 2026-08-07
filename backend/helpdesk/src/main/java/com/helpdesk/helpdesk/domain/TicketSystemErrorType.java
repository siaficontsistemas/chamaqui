package com.helpdesk.helpdesk.domain;

import java.util.Locale;

public enum TicketSystemErrorType {
	DATABASE("Banco de dados"), APPLICATION("Aplicação"), REPORT("Relatório");

	private final String label;
	TicketSystemErrorType(String label) { this.label = label; }
	public String getLabel() { return label; }
	public static TicketSystemErrorType fromCode(String code) {
		return code == null || code.isBlank() ? null : valueOf(code.trim().toUpperCase(Locale.ROOT));
	}
}
