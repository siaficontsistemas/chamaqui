package com.helpdesk.helpdesk.dto.report;

public record PersonalReportCompanyResponse(
	String companyName,
	long repliedTickets
) {
}
