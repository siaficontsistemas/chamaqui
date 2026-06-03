package com.helpdesk.helpdesk.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientCompanyRequest(
	@NotBlank(message = "Informe o nome da empresa cliente.")
	@Size(min = 3, max = 150, message = "O nome da empresa cliente deve ter entre 3 e 150 caracteres.")
	String companyName,
	@NotBlank(message = "Informe o CNPJ da empresa cliente.")
	@Size(min = 14, max = 20, message = "O CNPJ da empresa cliente deve ter entre 14 e 20 caracteres.")
	String companyDocument,
	@Size(max = 150, message = "O email da empresa cliente deve ter no máximo 150 caracteres.")
	String companyEmail,
	@Size(max = 30, message = "O telefone da empresa cliente deve ter no máximo 30 caracteres.")
	String companyPhoneNumber,
	@NotBlank(message = "Informe o email do administrador provedor responsável.")
	String createdByEmail
) {
}
