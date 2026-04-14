package com.helpdesk.helpdesk.dto.ticket;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TicketMessageResponse(
	UUID id,
	String authorName,
	String authorEmail,
	String authorRole,
	String message,
	boolean internal,
	List<TicketAttachmentResponse> attachments,
	OffsetDateTime createdAt
) {
}
