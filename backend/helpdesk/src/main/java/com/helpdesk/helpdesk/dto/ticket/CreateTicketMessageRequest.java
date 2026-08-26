package com.helpdesk.helpdesk.dto.ticket;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTicketMessageRequest(
	@NotBlank @Email String authorEmail,
	@Size(max = 5000) String message,
	UUID replyToMessageId
) {
	public CreateTicketMessageRequest(String authorEmail, String message) {
		this(authorEmail, message, null);
	}
}
