package com.helpdesk.helpdesk.dto.whatsapp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartWhatsappSessionRequest(
	@NotBlank @Email @Size(max = 150) String adminEmail,
	@Size(max = 500) String webhook,
	Boolean waitQrCode
) {
}
