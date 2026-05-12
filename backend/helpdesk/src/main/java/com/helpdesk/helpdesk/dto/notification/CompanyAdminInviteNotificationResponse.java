package com.helpdesk.helpdesk.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyAdminInviteNotificationResponse(
	UUID id,
	String requesterName,
	String requesterEmail,
	String requesterDocumentNumber,
	String requestedRole,
	String companyName,
	String companyType,
	String status,
	OffsetDateTime createdAt
) {
}
