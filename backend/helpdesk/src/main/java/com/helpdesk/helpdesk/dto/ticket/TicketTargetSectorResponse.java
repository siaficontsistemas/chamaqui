package com.helpdesk.helpdesk.dto.ticket;

import java.util.List;
import java.util.UUID;

public record TicketTargetSectorResponse(
	UUID id,
	String name,
	String slug,
	String description,
	boolean active,
	UUID companyOwnerId,
	String companyName,
	String companyDocument,
	String createdByEmail,
	int memberCount,
	List<TicketTargetAssigneeResponse> assignees
) {
}
