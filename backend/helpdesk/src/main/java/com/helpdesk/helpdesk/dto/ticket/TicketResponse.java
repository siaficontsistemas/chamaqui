package com.helpdesk.helpdesk.dto.ticket;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketResponse(
	UUID id,
	String protocol,
	String title,
	String description,
	String requesterName,
	String requesterEmail,
	String requesterPhoneNumber,
	String requesterDocumentNumber,
	String requesterCompanyName,
	String assignedToName,
	String assignedToEmail,
	String sectorName,
	String channel,
	String statusCode,
	String statusName,
	String priorityCode,
	String priorityName,
	OffsetDateTime openedAt,
	OffsetDateTime closedAt,
	String pendingTransferToName
) {
}
