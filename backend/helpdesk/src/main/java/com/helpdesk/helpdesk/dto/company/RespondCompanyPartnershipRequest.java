package com.helpdesk.helpdesk.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RespondCompanyPartnershipRequest(
	@NotBlank
	@Email
	String email
) {
}
