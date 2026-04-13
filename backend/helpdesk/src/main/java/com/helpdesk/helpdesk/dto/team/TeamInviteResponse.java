package com.helpdesk.helpdesk.dto.team;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TeamInviteResponse(
	UUID id,
	String invitedName,
	String email,
	String status,
	String invitedByEmail,
	String invitedByName,
	OffsetDateTime expiresAt,
	OffsetDateTime acceptedAt,
	OffsetDateTime updatedAt,
	List<UUID> sectorIds,
	List<String> sectorNames
) {
}
