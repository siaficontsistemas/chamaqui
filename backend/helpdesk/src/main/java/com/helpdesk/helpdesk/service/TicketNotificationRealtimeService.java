package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.realtime.TicketNotificationRealtimeEvent;
import com.helpdesk.helpdesk.realtime.TicketNotificationRealtimeSessionRegistry;

@Service
public class TicketNotificationRealtimeService {

	private final TicketNotificationRealtimeSessionRegistry sessionRegistry;

	public TicketNotificationRealtimeService(TicketNotificationRealtimeSessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	public void publishCreatedAfterCommit(
		Ticket ticket,
		TicketMessage message,
		User recipient,
		String notificationType,
		UUID notificationId
	) {
		if (ticket == null || recipient == null || notificationId == null) {
			return;
		}

		String recipientEmail = normalizeEmail(recipient.getEmail());
		UUID tenantOwnerUserId = resolveTenantOwnerUserId(ticket);
		if (recipientEmail == null || tenantOwnerUserId == null) {
			return;
		}

		TicketNotificationRealtimeEvent event = new TicketNotificationRealtimeEvent(
			notificationId,
			"CREATED",
			notificationType,
			notificationId,
			ticket.getId(),
			ticket.getProtocol(),
			ticket.getTitle(),
			ticket.getRequester() == null ? "" : ticket.getRequester().getFullName(),
			ticket.getSector() == null ? "" : ticket.getSector().getName(),
			resolveCompanyName(ticket),
			previewMessage(message),
			OffsetDateTime.now()
		);

		runAfterCommit(() -> sessionRegistry.sendToRecipient(tenantOwnerUserId, recipientEmail, event));
	}

	public void publishClearedAfterCommit(Ticket ticket, Collection<String> recipientEmails) {
		if (ticket == null || recipientEmails == null || recipientEmails.isEmpty()) {
			return;
		}

		UUID tenantOwnerUserId = resolveTenantOwnerUserId(ticket);
		if (tenantOwnerUserId == null) {
			return;
		}

		Set<String> normalizedEmails = new LinkedHashSet<>();
		for (String recipientEmail : recipientEmails) {
			String normalizedEmail = normalizeEmail(recipientEmail);
			if (normalizedEmail != null) {
				normalizedEmails.add(normalizedEmail);
			}
		}

		if (normalizedEmails.isEmpty()) {
			return;
		}

		TicketNotificationRealtimeEvent event = new TicketNotificationRealtimeEvent(
			UUID.randomUUID(),
			"CLEARED",
			"ticket",
			null,
			ticket.getId(),
			ticket.getProtocol(),
			ticket.getTitle(),
			ticket.getRequester() == null ? "" : ticket.getRequester().getFullName(),
			ticket.getSector() == null ? "" : ticket.getSector().getName(),
			resolveCompanyName(ticket),
			"",
			OffsetDateTime.now()
		);

		runAfterCommit(() -> normalizedEmails.forEach(
			recipientEmail -> sessionRegistry.sendToRecipient(tenantOwnerUserId, recipientEmail, event)
		));
	}

	private void runAfterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

	private UUID resolveTenantOwnerUserId(Ticket ticket) {
		if (ticket == null || ticket.getSector() == null || ticket.getSector().getCreatedBy() == null) {
			return null;
		}

		return ticket.getSector().getCreatedBy().getId();
	}

	private String resolveCompanyName(Ticket ticket) {
		if (ticket == null || ticket.getSector() == null || ticket.getSector().getCreatedBy() == null) {
			return "Empresa não informada";
		}

		String companyName = ticket.getSector().getCreatedBy().getCompanyName();
		if (companyName == null || companyName.isBlank()) {
			companyName = ticket.getSector().getCreatedBy().getFullName();
		}

		return companyName == null || companyName.isBlank() ? "Empresa não informada" : companyName;
	}

	private String previewMessage(TicketMessage message) {
		if (message == null || message.getMessage() == null || message.getMessage().isBlank()) {
			return "";
		}

		String preview = message.getMessage().trim();
		if (preview.length() > 140) {
			preview = preview.substring(0, 140).trim() + "...";
		}
		return preview;
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
