package com.helpdesk.helpdesk.domain;

import java.util.Locale;

public enum TicketCategory {
	QUESTION("Dúvida"), CLIENT_ERROR("Erro do Cliente"), SYSTEM_ERROR("Erro do Sistema"),
	DOCUMENTATION_ERROR("Erro de Documentação"), IMPLEMENTATION("Implementação");

	private final String label;
	TicketCategory(String label) { this.label = label; }
	public String getLabel() { return label; }
	public static TicketCategory fromCode(String code) {
		return code == null || code.isBlank() ? null : valueOf(code.trim().toUpperCase(Locale.ROOT));
	}
}
