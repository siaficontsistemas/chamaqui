package com.helpdesk.helpdesk.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
	@NotBlank @Size(min = 3, max = 150) String fullName,
	@NotBlank @Email @Size(max = 150) String email,
	@Size(max = 30) String phoneNumber,
	@NotBlank @Size(min = 11, max = 20) String documentNumber,
	@Size(max = 150) String companyName,
	@Size(max = 20) String companyDocument,
	@NotBlank @Size(min = 8, max = 60) String password,
	@NotBlank
	@Pattern(regexp = "admin|employee|user", message = "O perfil informado é inválido.")
	String role
) {
}
