package com.helpdesk.helpdesk.dto.calendar;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CalendarObligationResponse(
	UUID id,
	String title,
	String description,
	OffsetDateTime dueAt,
	OffsetDateTime reminderAt,
	OffsetDateTime completedAt,
	OffsetDateTime createdAt,
	String createdByName,
	String recipientName,
	String recipientDocumentNumber,
	String companyName,
	String status,
	boolean reminderActive
) {
}
