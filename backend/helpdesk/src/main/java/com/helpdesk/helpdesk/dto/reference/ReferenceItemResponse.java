package com.helpdesk.helpdesk.dto.reference;

import java.util.UUID;

public record ReferenceItemResponse(
	UUID id,
	String code,
	String name,
	Integer sortOrder
) {
}
