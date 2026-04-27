package com.helpdesk.helpdesk.dto.whatsapp;

public record WhatsappQrCodeResponse(
	String session,
	String qrCode,
	String status,
	String message,
	String data
) {
}
