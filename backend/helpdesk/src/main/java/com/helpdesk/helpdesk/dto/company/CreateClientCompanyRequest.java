package com.helpdesk.helpdesk.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClientCompanyRequest(
	@NotBlank(message = "Informe o nome do administrador.")
	@Size(min = 3, max = 150, message = "O nome do administrador deve ter entre 3 e 150 caracteres.")
	String fullName,
	@NotBlank(message = "Informe o email do administrador.")
	@Email(message = "Informe um email válido para o administrador.")
	@Size(max = 150, message = "O email do administrador deve ter no máximo 150 caracteres.")
	String email,
	@Size(max = 30, message = "O telefone do administrador deve ter no máximo 30 caracteres.")
	String phoneNumber,
	@NotBlank(message = "Informe o CPF do administrador.")
	@Size(min = 11, max = 20, message = "O CPF do administrador deve ter entre 11 e 20 caracteres.")
	String documentNumber,
	@NotBlank(message = "Informe a senha do administrador.")
	@Size(min = 8, max = 60, message = "A senha do administrador deve ter entre 8 e 60 caracteres.")
	String password,
	@NotBlank(message = "Informe o nome da empresa cliente.")
	@Size(min = 3, max = 150, message = "O nome da empresa cliente deve ter entre 3 e 150 caracteres.")
	String companyName,
	@NotBlank(message = "Informe o CNPJ da empresa cliente.")
	@Size(min = 14, max = 20, message = "O CNPJ da empresa cliente deve ter entre 14 e 20 caracteres.")
	String companyDocument,
	@NotBlank(message = "Informe o email do administrador provedor responsável.")
	@Email(message = "Informe um email válido para o administrador provedor.")
	@Pattern(regexp = ".+@.+\\..+", message = "Informe um email válido para o administrador provedor.")
	String createdByEmail
) {
}
