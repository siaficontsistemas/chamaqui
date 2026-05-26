package com.helpdesk.helpdesk.dto.platformadmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PlatformAdminLoginRequest(
	@NotBlank(message = "Informe o email do administrador da plataforma.")
	@Email(message = "Informe um email válido.")
	String email,

	@NotBlank(message = "Informe a senha do administrador da plataforma.")
	String password
) {
}
