package com.helpdesk.helpdesk.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDataSubjectRequestRequest(
	@NotBlank(message = "Informe o tipo da solicitação.")
	@Pattern(
		regexp = "ACCESS|CORRECTION|DELETION|PORTABILITY|OPPOSITION|DECISION_REVIEW",
		message = "O tipo de solicitação informado é inválido."
	)
	String requestType,
	@NotBlank(message = "Descreva a solicitação para continuarmos o atendimento.")
	@Size(max = 4000, message = "A descrição da solicitação deve ter no máximo 4000 caracteres.")
	String requestDescription
) {
}
