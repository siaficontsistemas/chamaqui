package com.helpdesk.helpdesk.dto.ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CloseTicketRequest(
	@NotBlank @Email String authorEmail
) {
}
