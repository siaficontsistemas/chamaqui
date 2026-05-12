package com.helpdesk.helpdesk.dto.company;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyAdminInviteResponse(
	UUID id,
	String invitedName,
	String invitedEmail,
	String invitedDocumentNumber,
	String companyName,
	String companyType,
	String deliveryChannel,
	OffsetDateTime expiresAt
) {
}
