package com.helpdesk.helpdesk.dto.sector;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSectorRequest(
	@NotBlank @Size(min = 2, max = 120) String name,
	@Size(max = 255) String description,
	@NotBlank @Email String createdByEmail
) {
}
