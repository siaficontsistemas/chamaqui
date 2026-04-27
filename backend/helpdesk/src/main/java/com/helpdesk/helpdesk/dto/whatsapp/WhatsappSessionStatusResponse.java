package com.helpdesk.helpdesk.dto.whatsapp;

public record WhatsappSessionStatusResponse(
	String session,
	String status,
	boolean connected,
	String webhook,
	String message,
	String data
) {
}
