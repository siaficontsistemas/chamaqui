package com.helpdesk.helpdesk.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketClosureNotificationResponse(
	UUID id,
	UUID ticketId,
	String ticketProtocol,
	String ticketTitle,
	String sectorName,
	String companyName,
	String closedByName,
	OffsetDateTime createdAt
) {
}
