package com.helpdesk.helpdesk.dto.calendar;

import java.time.OffsetDateTime;
import java.util.List;
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
	List<String> recipientNames,
	List<String> recipientDocumentNumbers,
	String companyName,
	String status,
	boolean reminderActive
) {
}
