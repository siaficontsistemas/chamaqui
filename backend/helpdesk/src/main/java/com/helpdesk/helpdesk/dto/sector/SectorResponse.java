package com.helpdesk.helpdesk.dto.sector;

import java.util.UUID;

public record SectorResponse(
	UUID id,
	String name,
	String slug,
	String description,
	boolean active,
	String createdByEmail,
	int memberCount
) {
}
