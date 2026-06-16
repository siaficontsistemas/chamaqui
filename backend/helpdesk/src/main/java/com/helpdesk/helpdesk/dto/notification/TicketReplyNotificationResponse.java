package com.helpdesk.helpdesk.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketReplyNotificationResponse(
	UUID id,
	UUID ticketId,
	String ticketProtocol,
	String ticketTitle,
	String requesterName,
	String sectorName,
	String companyName,
	String requesterCompanyName,
	String messagePreview,
	String status,
	OffsetDateTime createdAt
) {
}
