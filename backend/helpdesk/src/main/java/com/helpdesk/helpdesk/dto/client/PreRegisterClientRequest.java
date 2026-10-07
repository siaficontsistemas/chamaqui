package com.helpdesk.helpdesk.dto.client;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PreRegisterClientRequest(
	@NotBlank @Size(min = 3, max = 150) String fullName,
	@NotBlank @Size(max = 30) String phoneNumber,
	@NotNull UUID companyOwnerId
) {}
