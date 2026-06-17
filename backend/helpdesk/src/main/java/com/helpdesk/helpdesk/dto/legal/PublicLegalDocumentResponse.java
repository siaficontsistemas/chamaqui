package com.helpdesk.helpdesk.dto.legal;

import java.time.OffsetDateTime;
import java.util.List;

public record PublicLegalDocumentResponse(
	String type,
	String title,
	String version,
	OffsetDateTime effectiveAt,
	String summary,
	List<PublicLegalDocumentSectionResponse> sections
) {
}
