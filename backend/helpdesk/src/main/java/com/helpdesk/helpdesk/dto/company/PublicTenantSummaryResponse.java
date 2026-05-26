package com.helpdesk.helpdesk.dto.company;

public record PublicTenantSummaryResponse(
	String companyName,
	String subdomain,
	String schemaName,
	String accessUrl
) {
}
