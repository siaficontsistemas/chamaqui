package com.helpdesk.helpdesk.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TeamMembershipNotificationResponse(
	UUID id,
	String type,
	String sectorName,
	String companyName,
	String removedByName,
	OffsetDateTime createdAt
) {
}
