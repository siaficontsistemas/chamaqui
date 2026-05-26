package com.helpdesk.helpdesk.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
	@NotBlank(message = "Informe o email da conta.")
	@Email(message = "Informe um email válido.")
	@Size(max = 150, message = "O email deve ter no máximo 150 caracteres.")
	String currentEmail,
	@NotBlank(message = "Informe a nova senha.")
	@Size(min = 8, max = 60, message = "A nova senha deve ter entre 8 e 60 caracteres.")
	String newPassword,
	@NotBlank(message = "Repita a nova senha.")
	@Size(min = 8, max = 60, message = "A confirmação da senha deve ter entre 8 e 60 caracteres.")
	String confirmPassword
) {
}
