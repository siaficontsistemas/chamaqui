package com.helpdesk.helpdesk.dto.calendar;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCalendarObligationRequest(
	@NotBlank @Size(max = 180) String title,
	@Size(max = 2000) String description,
	@NotNull OffsetDateTime dueAt,
	OffsetDateTime reminderAt,
	@NotBlank String recipientDocumentNumber,
	@NotBlank String updatedByEmail
) {
}
