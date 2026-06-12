package com.helpdesk.helpdesk.domain;

public enum CalendarObligationPriority {
	LOW,
	MEDIUM,
	HIGH;

	public static CalendarObligationPriority fromValue(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		try {
			return CalendarObligationPriority.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
