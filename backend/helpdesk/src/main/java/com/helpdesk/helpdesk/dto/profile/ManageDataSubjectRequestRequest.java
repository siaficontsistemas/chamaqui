package com.helpdesk.helpdesk.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ManageDataSubjectRequestRequest(
	@NotBlank(message = "Informe o novo status da solicitação.")
	@Pattern(
		regexp = "OPEN|IN_PROGRESS|COMPLETED|REJECTED",
		message = "O status informado é inválido."
	)
	String status,
	@Size(max = 4000, message = "O resumo da resposta deve ter no máximo 4000 caracteres.")
	String responseSummary,
	@Size(max = 4000, message = "As observações internas devem ter no máximo 4000 caracteres.")
	String internalNotes
) {
}
