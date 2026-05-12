package com.helpdesk.helpdesk.dto.team;

import java.util.List;
import java.util.UUID;

public record TeamMemberResponse(
	UUID userId,
	String fullName,
	String email,
	String documentNumber,
	String role,
	String status,
	List<UUID> sectorIds
) {
}
