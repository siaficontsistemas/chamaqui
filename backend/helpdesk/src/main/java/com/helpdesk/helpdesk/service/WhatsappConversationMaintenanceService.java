package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WhatsappConversationMaintenanceService {

	private static final Logger logger = LoggerFactory.getLogger(WhatsappConversationMaintenanceService.class);

	private final WhatsappWebhookService whatsappWebhookService;

	public WhatsappConversationMaintenanceService(WhatsappWebhookService whatsappWebhookService) {
		this.whatsappWebhookService = whatsappWebhookService;
	}

	@Scheduled(cron = "0 0 * * * *")
	public void closeInactiveNormalConversations() {
		int closedCount = whatsappWebhookService.closeInactiveNormalConversations(OffsetDateTime.now().minusDays(2));
		if (closedCount > 0) {
			logger.info("Conversas normais do WhatsApp encerradas por inatividade: {}", closedCount);
		}
	}
}
