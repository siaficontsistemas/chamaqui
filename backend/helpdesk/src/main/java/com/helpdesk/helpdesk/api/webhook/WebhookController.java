package com.helpdesk.helpdesk.api.webhook;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.service.WhatsappWebhookService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

	private final WhatsappWebhookService whatsappWebhookService;

	public WebhookController(WhatsappWebhookService whatsappWebhookService) {
		this.whatsappWebhookService = whatsappWebhookService;
	}

	@PostMapping
	public ResponseEntity<Void> receive(
		@RequestBody(required = false) String payload,
		HttpServletRequest request
	) {
		whatsappWebhookService.receive(payload, request);
		return ResponseEntity.ok().build();
	}
}
