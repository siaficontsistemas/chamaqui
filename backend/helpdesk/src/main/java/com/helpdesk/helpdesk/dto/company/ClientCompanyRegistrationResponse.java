package com.helpdesk.helpdesk.dto.company;

import java.util.UUID;

public record ClientCompanyRegistrationResponse(
	UUID companyOwnerId,
	String companyName,
	String companyDocument,
	String companyEmail,
	String companyPhoneNumber,
	String subdomain
) {
}
