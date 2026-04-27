package com.helpdesk.helpdesk.dto.whatsapp;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendWhatsappTestMessageRequest(
	@NotBlank @Email @Size(max = 150) String adminEmail,
	@NotBlank @Size(max = 40) String phone,
	@NotBlank @Size(max = 5000) String message
) {
}
