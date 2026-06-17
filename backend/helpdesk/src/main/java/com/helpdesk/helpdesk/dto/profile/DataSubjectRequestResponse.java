package com.helpdesk.helpdesk.dto.profile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DataSubjectRequestResponse(
	UUID id,
	String requestType,
	String status,
	String requesterFullName,
	String requesterEmail,
	String requestDescription,
	String responseSummary,
	String internalNotes,
	OffsetDateTime requestedAt,
	OffsetDateTime dueAt,
	OffsetDateTime resolvedAt
) {
}
