package com.helpdesk.helpdesk.dto.ticket;

import java.util.UUID;

public record TicketTargetAssigneeResponse(
	UUID id,
	String fullName,
	String email
) {
}
