package com.helpdesk.helpdesk.dto.calendar;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CalendarObligationResponse(
	UUID id,
	String title,
	String description,
	String priority,
	OffsetDateTime dueAt,
	OffsetDateTime reminderAt,
	OffsetDateTime completedAt,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt,
	String createdByName,
	List<String> recipientNames,
	List<String> recipientDocumentNumbers,
	String companyName,
	UUID linkedCompanyOwnerId,
	String linkedCompanyName,
	List<CalendarLinkedTicketResponse> linkedTickets,
	String status,
	boolean reminderActive
) {
}
