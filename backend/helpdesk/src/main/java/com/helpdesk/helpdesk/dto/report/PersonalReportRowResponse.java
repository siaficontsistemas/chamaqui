package com.helpdesk.helpdesk.dto.report;

public record PersonalReportRowResponse(
	String year,
	String month,
	long createdTickets,
	long repliedTickets
) {
}
