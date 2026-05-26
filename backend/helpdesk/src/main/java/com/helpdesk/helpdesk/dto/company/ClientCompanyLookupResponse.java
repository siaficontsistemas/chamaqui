package com.helpdesk.helpdesk.dto.company;

import java.util.UUID;

public record ClientCompanyLookupResponse(
	String status,
	String message,
	UUID companyOwnerId,
	String companyName,
	String companyDocument,
	String adminName,
	String adminEmail
) {
}
