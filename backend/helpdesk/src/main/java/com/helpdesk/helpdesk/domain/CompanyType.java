package com.helpdesk.helpdesk.domain;

import java.util.Locale;

public enum CompanyType {
	REQUESTER,
	RESPONDER;

	public static CompanyType fromValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return switch (value.trim().toUpperCase(Locale.ROOT)) {
			case "REQUESTER" -> REQUESTER;
			case "RESPONDER" -> RESPONDER;
			default -> throw new IllegalArgumentException("O tipo de empresa informado é inválido.");
		};
	}
}
