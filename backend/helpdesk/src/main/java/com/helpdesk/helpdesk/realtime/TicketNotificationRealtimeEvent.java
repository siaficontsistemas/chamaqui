package com.helpdesk.helpdesk.realtime;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketNotificationRealtimeEvent(
	UUID eventId,
	String action,
	String notificationType,
	UUID notificationId,
	UUID ticketId,
	String ticketProtocol,
	String ticketTitle,
	String requesterName,
	String sectorName,
	String companyName,
	String messagePreview,
	OffsetDateTime occurredAt
) {
}
