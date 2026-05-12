package com.helpdesk.helpdesk.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyAdminInviteRequest(
	@NotBlank(message = "Informe o nome da pessoa convidada.")
	@Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres.")
	String fullName,
	@NotBlank(message = "Informe o email da pessoa convidada.")
	@Email(message = "Informe um email válido.")
	@Size(max = 150, message = "O email deve ter no máximo 150 caracteres.")
	String email,
	@NotBlank(message = "Informe o CPF da pessoa convidada.")
	@Size(min = 11, max = 20, message = "O CPF deve ter entre 11 e 20 caracteres.")
	String documentNumber,
	@NotBlank(message = "Informe o email do administrador responsável.")
	@Email(message = "Informe um email válido para o administrador.")
	String invitedByEmail
) {
}
