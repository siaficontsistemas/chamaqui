package com.helpdesk.helpdesk.dto.company;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LinkExistingClientCompanyRequest(
	@NotNull(message = "Informe a empresa cliente que deve ser vinculada.")
	UUID companyOwnerId,
	@NotBlank(message = "Informe o email do administrador provedor responsável.")
	@Email(message = "Informe um email válido para o administrador provedor.")
	@Pattern(regexp = ".+@.+\\..+", message = "Informe um email válido para o administrador provedor.")
	String createdByEmail
) {
}
