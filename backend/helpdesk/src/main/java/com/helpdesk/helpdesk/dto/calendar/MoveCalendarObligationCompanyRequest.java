package com.helpdesk.helpdesk.dto.calendar;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MoveCalendarObligationCompanyRequest(
	@NotBlank String email,
	@NotNull UUID linkedCompanyOwnerId
) {
}
