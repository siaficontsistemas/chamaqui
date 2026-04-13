package com.helpdesk.helpdesk.dto.ticket;

public record TicketSummaryResponse(
	long total,
	long open,
	long inProgress,
	long closed
) {
}
