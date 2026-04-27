package com.helpdesk.helpdesk.dto.company;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public record CreateCompanyPartnershipRequest(
	@NotNull
	@Email
	String requesterEmail,
	@NotNull
	UUID targetCompanyId
) {
}
