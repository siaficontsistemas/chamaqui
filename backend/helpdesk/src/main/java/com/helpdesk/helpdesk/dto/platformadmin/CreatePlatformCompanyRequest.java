package com.helpdesk.helpdesk.dto.platformadmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformCompanyRequest(
	@NotBlank(message = "Informe o nome da empresa.")
	@Size(max = 150, message = "O nome da empresa deve ter no máximo 150 caracteres.")
	String companyName,

	@NotBlank(message = "Informe o CNPJ da empresa.")
	String companyDocument,

	@NotBlank(message = "Informe o subdomínio desejado.")
	@Size(max = 80, message = "O subdomínio deve ter no máximo 80 caracteres.")
	String subdomain,

	@NotBlank(message = "Informe o nome do administrador.")
	@Size(max = 150, message = "O nome do administrador deve ter no máximo 150 caracteres.")
	String adminFullName,

	@NotBlank(message = "Informe o email do administrador.")
	@Email(message = "Informe um email válido para o administrador.")
	String adminEmail,

	String adminPhoneNumber,

	@NotBlank(message = "Informe o CPF do administrador.")
	String adminDocumentNumber,

	@NotBlank(message = "Informe a senha inicial do administrador.")
	@Size(min = 6, message = "A senha inicial deve ter pelo menos 6 caracteres.")
	String adminPassword
) {
}
