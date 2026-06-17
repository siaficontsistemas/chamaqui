package com.helpdesk.helpdesk.dto.legal;

import java.util.List;

public record PublicLegalDocumentSectionResponse(
	String title,
	List<String> paragraphs
) {
}
