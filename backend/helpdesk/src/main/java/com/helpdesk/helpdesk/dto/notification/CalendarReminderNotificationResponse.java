package com.helpdesk.helpdesk.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CalendarReminderNotificationResponse(
	UUID id,
	UUID obligationId,
	String obligationTitle,
	String obligationDescription,
	OffsetDateTime dueAt,
	OffsetDateTime reminderAt,
	String createdByName,
	String companyName,
	String status,
	OffsetDateTime createdAt
) {
}
