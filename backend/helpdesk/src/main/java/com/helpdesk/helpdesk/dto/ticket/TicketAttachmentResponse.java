package com.helpdesk.helpdesk.dto.ticket;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketAttachmentResponse(
	UUID id,
	String originalFileName,
	String contentType,
	long sizeBytes,
	String uploadedByName,
	String uploadedByEmail,
	OffsetDateTime createdAt
) {
}
