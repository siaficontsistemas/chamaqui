package com.helpdesk.helpdesk.dto.company;

import java.util.UUID;

public record PublicCompanyBrandingResponse(
	boolean tenantResolved,
	UUID ownerUserId,
	String companyName,
	String companyType,
	String subdomain,
	String logoUrl,
	String loginLogoUrl
) {
}
