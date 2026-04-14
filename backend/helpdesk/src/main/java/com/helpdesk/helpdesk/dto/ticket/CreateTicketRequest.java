package com.helpdesk.helpdesk.dto.ticket;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
	@NotBlank @Size(min = 3, max = 180) String title,
	@NotBlank @Size(min = 10) String description,
	@NotNull UUID companyOwnerId,
	@NotNull UUID sectorId,
	@NotBlank String priorityCode,
	@NotBlank @Email String requesterEmail,
	@Email @Size(max = 255) String copyEmail
) {
}
