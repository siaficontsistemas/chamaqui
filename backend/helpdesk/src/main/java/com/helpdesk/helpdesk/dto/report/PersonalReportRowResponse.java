package com.helpdesk.helpdesk.dto.report;

import java.util.List;

public record PersonalReportRowResponse(
	String year,
	String month,
	long createdTickets,
	long repliedTickets,
	List<PersonalReportCompanyResponse> repliedCompanies
) {
}
