package com.helpdesk.helpdesk.dto.ticket;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequestTicketTransferRequest(
	@NotBlank @Email String authorEmail,
	@NotNull UUID recipientUserId
) {
}
