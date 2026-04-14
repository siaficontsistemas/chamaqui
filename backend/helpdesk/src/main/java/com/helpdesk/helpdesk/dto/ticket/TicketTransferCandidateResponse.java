package com.helpdesk.helpdesk.dto.ticket;

import java.util.UUID;

public record TicketTransferCandidateResponse(
	UUID userId,
	String fullName,
	String email
) {
}
