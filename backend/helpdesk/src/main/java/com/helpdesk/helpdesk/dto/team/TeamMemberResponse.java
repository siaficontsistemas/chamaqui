package com.helpdesk.helpdesk.dto.team;

import java.util.List;
import java.util.UUID;

public record TeamMemberResponse(
	UUID userId,
	String fullName,
	String email,
	String documentNumber,
	UUID companyOwnerId,
	String companyName,
	String role,
	String status,
	List<UUID> sectorIds
) {
}
