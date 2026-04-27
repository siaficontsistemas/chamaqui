package com.helpdesk.helpdesk.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyPartnershipNotificationResponse(
	UUID id,
	UUID partnershipId,
	String eventType,
	String actorName,
	String actorCompanyName,
	UUID requesterCompanyId,
	String requesterCompanyName,
	UUID targetCompanyId,
	String targetCompanyName,
	String status,
	boolean canRespond,
	OffsetDateTime createdAt
) {
}
