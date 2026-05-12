package com.helpdesk.helpdesk.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
	@NotBlank @Email @Size(max = 150) String currentEmail,
	@NotBlank @Size(min = 3, max = 150) String fullName,
	@NotBlank @Email @Size(max = 150) String email,
	@Size(max = 30) String phoneNumber,
	@Size(max = 150) String companyName,
	@Size(max = 20) String companyDocument
) {
}
