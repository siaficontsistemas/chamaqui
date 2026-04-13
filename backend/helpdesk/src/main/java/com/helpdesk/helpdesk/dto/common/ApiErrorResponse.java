package com.helpdesk.helpdesk.dto.common;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorResponse(
	String message,
	String error,
	OffsetDateTime timestamp,
	Map<String, String> fieldErrors
) {
}
