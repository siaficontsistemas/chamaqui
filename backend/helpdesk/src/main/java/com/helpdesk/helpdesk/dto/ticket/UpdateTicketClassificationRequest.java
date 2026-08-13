package com.helpdesk.helpdesk.dto.ticket;

import jakarta.validation.constraints.Email;

public record UpdateTicketClassificationRequest(
	String typeCode,
	String systemAreaCode,
	@jakarta.validation.constraints.NotBlank @Email String authorEmail
) {
}
