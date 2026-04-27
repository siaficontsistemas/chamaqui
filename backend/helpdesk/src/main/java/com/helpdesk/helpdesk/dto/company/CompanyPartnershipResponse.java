package com.helpdesk.helpdesk.dto.company;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyPartnershipResponse(
	UUID id,
	String status,
	UUID requesterCompanyId,
	String requesterCompanyName,
	String requesterCompanyDocument,
	UUID targetCompanyId,
	String targetCompanyName,
	String targetCompanyDocument,
	OffsetDateTime createdAt,
	OffsetDateTime respondedAt,
	boolean canRespond,
	boolean outgoing
) {
}
