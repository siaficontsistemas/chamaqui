package com.helpdesk.helpdesk.tenant;

import java.util.UUID;

public record ResolvedTenant(
	UUID companyId,
	UUID ownerUserId,
	String companyName,
	String companyType,
	String subdomain,
	String schemaName,
	String logoUrl,
	String loginLogoUrl
) {
}
