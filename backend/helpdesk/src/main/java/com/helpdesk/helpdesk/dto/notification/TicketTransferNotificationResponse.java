package com.helpdesk.helpdesk.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketTransferNotificationResponse(
	UUID id,
	UUID ticketId,
	String ticketProtocol,
	String ticketTitle,
	String requesterName,
	String sectorName,
	String companyName,
	String requesterCompanyName,
	String senderName,
	String recipientName,
	String status,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt,
	OffsetDateTime respondedAt
) {
}
