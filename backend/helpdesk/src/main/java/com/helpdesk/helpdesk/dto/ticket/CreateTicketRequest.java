package com.helpdesk.helpdesk.dto.ticket;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
	@NotBlank @Size(min = 10) String description,
	@NotNull UUID companyOwnerId,
	@NotNull UUID sectorId,
	UUID assignedToUserId,
	@NotBlank String priorityCode,
	@NotBlank @Email String requesterEmail,
	@Email @Size(max = 255) String copyEmail,
	String categoryCode,
	String systemErrorTypeCode
) {
	public CreateTicketRequest(String description, UUID companyOwnerId, UUID sectorId, UUID assignedToUserId,
		String priorityCode, String requesterEmail, String copyEmail) {
		this(description, companyOwnerId, sectorId, assignedToUserId, priorityCode, requesterEmail, copyEmail, null, null);
	}
}
