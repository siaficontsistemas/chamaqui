package com.helpdesk.helpdesk.dto.ticket;

import jakarta.validation.constraints.NotBlank;

public record UpdateTicketClassificationRequest(
	@NotBlank String categoryCode,
	String systemErrorTypeCode,
	@NotBlank String authorEmail
) {}
