package com.helpdesk.helpdesk.dto.ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTicketTitleRequest(
	@NotBlank @Size(max = 180) String title,
	@NotBlank @Email String authorEmail
) {
}
