package com.helpdesk.helpdesk.service;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.realtime.TicketNotificationRealtimeEvent;
import com.helpdesk.helpdesk.realtime.TicketNotificationRealtimeSessionRegistry;

@ExtendWith(MockitoExtension.class)
class TicketNotificationRealtimeServiceTest {

	@Mock
	private TicketNotificationRealtimeSessionRegistry sessionRegistry;

	private TicketNotificationRealtimeService service;

	@BeforeEach
	void setUp() {
		service = new TicketNotificationRealtimeService(sessionRegistry);
	}

	@Test
	void shouldPublishCreatedEventForTicketReply() {
		Ticket ticket = ticket();
		TicketMessage message = new TicketMessage();
		message.setMessage("Cliente enviou um novo comprovante para analise.");
		User recipient = user(UUID.randomUUID(), "funcionario@empresa.com", "Funcionario");
		UUID notificationId = UUID.randomUUID();

		service.publishCreatedAfterCommit(ticket, message, recipient, "ticket-reply", notificationId);

		ArgumentCaptor<TicketNotificationRealtimeEvent> eventCaptor =
			ArgumentCaptor.forClass(TicketNotificationRealtimeEvent.class);
		verify(sessionRegistry).sendToRecipient(
			eq(ticket.getSector().getCreatedBy().getId()),
			eq("funcionario@empresa.com"),
			eventCaptor.capture()
		);

		TicketNotificationRealtimeEvent event = eventCaptor.getValue();
		assertEquals(notificationId, event.eventId());
		assertEquals("CREATED", event.action());
		assertEquals("ticket-reply", event.notificationType());
		assertEquals(ticket.getId(), event.ticketId());
		assertEquals("Fiscal", event.sectorName());
		assertEquals("Mensagem nova do cliente", event.ticketTitle());
		assertNotNull(event.occurredAt());
	}

	@Test
	void shouldPublishSingleClearEventPerDistinctRecipient() {
		Ticket ticket = ticket();

		service.publishClearedAfterCommit(
			ticket,
			List.of("funcionario@empresa.com", "FUNCIONARIO@EMPRESA.COM", "admin@empresa.com")
		);

		ArgumentCaptor<TicketNotificationRealtimeEvent> firstEventCaptor =
			ArgumentCaptor.forClass(TicketNotificationRealtimeEvent.class);
		ArgumentCaptor<TicketNotificationRealtimeEvent> secondEventCaptor =
			ArgumentCaptor.forClass(TicketNotificationRealtimeEvent.class);

		verify(sessionRegistry).sendToRecipient(
			eq(ticket.getSector().getCreatedBy().getId()),
			eq("funcionario@empresa.com"),
			firstEventCaptor.capture()
		);
		verify(sessionRegistry).sendToRecipient(
			eq(ticket.getSector().getCreatedBy().getId()),
			eq("admin@empresa.com"),
			secondEventCaptor.capture()
		);
		verifyNoMoreInteractions(sessionRegistry);

		assertEquals("CLEARED", firstEventCaptor.getValue().action());
		assertEquals(ticket.getId(), firstEventCaptor.getValue().ticketId());
		assertEquals("CLEARED", secondEventCaptor.getValue().action());
		assertEquals(ticket.getId(), secondEventCaptor.getValue().ticketId());
	}

	@Test
	void shouldSkipCreatedEventWhenRecipientEmailIsMissing() {
		Ticket ticket = ticket();
		User recipientWithoutEmail = user(UUID.randomUUID(), "", "Sem Email");

		service.publishCreatedAfterCommit(ticket, null, recipientWithoutEmail, "ticket-assignment", UUID.randomUUID());

		verify(sessionRegistry, never()).sendToRecipient(any(), any(), any());
	}

	private Ticket ticket() {
		User companyOwner = user(UUID.randomUUID(), "admin@empresa.com", "Empresa Exemplo");
		companyOwner.setCompanyName("Empresa Exemplo");
		User requester = user(UUID.randomUUID(), "cliente@empresa.com", "Cliente");

		Sector sector = new Sector();
		sector.setName("Fiscal");
		sector.setSlug("fiscal");
		sector.setCreatedBy(companyOwner);

		Ticket ticket = new Ticket();
		ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
		ticket.setProtocol("CA-2026-0001");
		ticket.setTitle("Mensagem nova do cliente");
		ticket.setRequester(requester);
		ticket.setSector(sector);
		return ticket;
	}

	private User user(UUID id, String email, String fullName) {
		User user = new User();
		ReflectionTestUtils.setField(user, "id", id);
		user.setEmail(email);
		user.setFullName(fullName);
		return user;
	}
}
