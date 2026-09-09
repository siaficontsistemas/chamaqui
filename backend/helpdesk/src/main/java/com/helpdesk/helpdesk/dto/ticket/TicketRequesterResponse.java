package com.helpdesk.helpdesk.dto.ticket;

import java.util.UUID;

public record TicketRequesterResponse(
	UUID id,
	String fullName,
	String email,
	String phoneNumber
) {
}
