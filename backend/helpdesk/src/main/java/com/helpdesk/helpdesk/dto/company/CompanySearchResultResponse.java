package com.helpdesk.helpdesk.dto.company;

import java.util.UUID;

public record CompanySearchResultResponse(
	UUID companyId,
	String companyName,
	String companyDocument,
	String companyType
) {
}
