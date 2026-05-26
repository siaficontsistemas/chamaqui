package com.helpdesk.helpdesk.dto.ticket;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record DeleteTicketsRequest(
	@NotEmpty List<@NotNull UUID> ticketIds,
	@NotBlank @Email String authorEmail
) {
}
