package com.helpdesk.helpdesk.dto.company;

import java.util.UUID;

public record ClientCompanyRegistrationResponse(
	UUID companyOwnerId,
	String companyName,
	String companyDocument,
	String adminName,
	String adminEmail,
	String subdomain
) {
}
