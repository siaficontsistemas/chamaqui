package com.helpdesk.helpdesk.dto.platformadmin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PlatformCompanySummaryResponse(
	UUID companyId,
	String companyName,
	String companyDocument,
	String subdomain,
	String schemaName,
	boolean active,
	String adminFullName,
	String adminEmail,
	String adminStatus,
	long activeUsersCount,
	OffsetDateTime createdAt
) {
}
