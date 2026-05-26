package com.helpdesk.helpdesk.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
	@NotBlank(message = "Informe o token de redefinição de senha.")
	String token,
	@NotBlank(message = "Informe a nova senha.")
	@Size(min = 8, max = 60, message = "A nova senha deve ter entre 8 e 60 caracteres.")
	String password,
	@NotBlank(message = "Repita a nova senha.")
	@Size(min = 8, max = 60, message = "A confirmação da senha deve ter entre 8 e 60 caracteres.")
	String confirmPassword
) {
}
