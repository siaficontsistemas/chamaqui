package com.helpdesk.helpdesk.dto.company;

public record CompanyLogoResponse(
	String companyName,
	String logoUrl,
	String loginLogoUrl
) {
}
