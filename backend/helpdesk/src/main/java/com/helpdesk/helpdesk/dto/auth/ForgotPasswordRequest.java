package com.helpdesk.helpdesk.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
	@NotBlank(message = "Informe o email para recuperar a senha.")
	@Email(message = "Informe um email válido.")
	@Size(max = 150, message = "O email deve ter no máximo 150 caracteres.")
	String email
) {
}
