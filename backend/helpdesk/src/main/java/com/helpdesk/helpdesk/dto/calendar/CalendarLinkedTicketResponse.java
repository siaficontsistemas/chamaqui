package com.helpdesk.helpdesk.dto.calendar;

import java.util.UUID;

public record CalendarLinkedTicketResponse(
	UUID id,
	String protocol,
	String title,
	String statusCode,
	String statusName,
	String responsibleName
) {
}
