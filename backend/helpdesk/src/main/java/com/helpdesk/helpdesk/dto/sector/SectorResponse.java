package com.helpdesk.helpdesk.dto.sector;

import java.util.UUID;

public record SectorResponse(
	UUID id,
	String name,
	String slug,
	String description,
	boolean active,
	UUID companyOwnerId,
	String companyName,
	String companyDocument,
	String createdByEmail,
	int memberCount
) {
}
