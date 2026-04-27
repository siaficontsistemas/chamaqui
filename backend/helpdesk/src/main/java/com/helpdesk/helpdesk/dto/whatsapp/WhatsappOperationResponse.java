package com.helpdesk.helpdesk.dto.whatsapp;

public record WhatsappOperationResponse(
	String session,
	String operation,
	boolean success,
	String message,
	String data
) {
}
