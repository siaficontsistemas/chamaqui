package com.helpdesk.helpdesk.dto.calendar;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateCalendarObligationTicketsRequest(
	@NotNull List<UUID> linkedTicketIds,
	@NotBlank String updatedByEmail
) {
}
