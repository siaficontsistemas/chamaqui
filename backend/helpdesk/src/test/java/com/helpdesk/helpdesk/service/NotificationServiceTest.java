package com.helpdesk.helpdesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.ticket.RespondTicketTransferRequest;
import com.helpdesk.helpdesk.repository.CalendarObligationRepository;
import com.helpdesk.helpdesk.repository.CalendarReminderNotificationRepository;
import com.helpdesk.helpdesk.repository.CompanyPartnershipNotificationRepository;
import com.helpdesk.helpdesk.repository.TeamMembershipNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketAssignmentNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketClosureNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketMessageRepository;
import com.helpdesk.helpdesk.repository.TicketReplyNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.TicketTransferNotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock
	private TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository;

	@Mock
	private TicketTransferNotificationRepository ticketTransferNotificationRepository;

	@Mock
	private TeamMembershipNotificationRepository teamMembershipNotificationRepository;

	@Mock
	private CalendarReminderNotificationRepository calendarReminderNotificationRepository;

	@Mock
	private CompanyPartnershipNotificationRepository companyPartnershipNotificationRepository;

	@Mock
	private TicketClosureNotificationRepository ticketClosureNotificationRepository;

	@Mock
	private TicketReplyNotificationRepository ticketReplyNotificationRepository;

	@Mock
	private TicketMessageRepository ticketMessageRepository;

	@Mock
	private CalendarObligationRepository calendarObligationRepository;

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private TenantAccessService tenantAccessService;

	@Mock
	private ScopedUserLookupService scopedUserLookupService;

	@Mock
	private CalendarReminderWhatsappDispatchService calendarReminderWhatsappDispatchService;

	@InjectMocks
	private NotificationService notificationService;

	@Test
	void shouldAssignTicketToNewResponsibleWhenTransferIsAccepted() {
		User formerAssignee = user("former@empresa.com", "Former Assignee");
		User newAssignee = user("new@empresa.com", "New Assignee");
		Ticket ticket = new Ticket();
		TicketTransferNotification notification = new TicketTransferNotification();
		UUID notificationId = UUID.randomUUID();
		setField(ticket, "id", UUID.randomUUID());
		ticket.setAssignedTo(formerAssignee);
		ticket.setPendingTransferTo(newAssignee);
		ticket.setPendingTransferRequestedBy(formerAssignee);
		notification.setTicket(ticket);
		notification.setSender(formerAssignee);
		notification.setRecipient(newAssignee);
		notification.setStatus(TicketTransferStatus.PENDING);
		setField(notification, "id", notificationId);

		when(ticketTransferNotificationRepository.findDetailedById(notificationId))
			.thenReturn(Optional.of(notification));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant(newAssignee.getEmail()))
			.thenReturn(Optional.of(newAssignee));

		notificationService.acceptTicketTransfer(
			notificationId,
			new RespondTicketTransferRequest(newAssignee.getEmail())
		);

		assertSame(newAssignee, ticket.getAssignedTo());
		assertNull(ticket.getPendingTransferTo());
		assertNull(ticket.getPendingTransferRequestedBy());
		assertNull(ticket.getPendingTransferRequestedAt());
		assertEquals(TicketTransferStatus.ACCEPTED, notification.getStatus());
		verify(ticketRepository).save(ticket);
		verify(ticketTransferNotificationRepository).save(notification);
	}

	private User user(String email, String fullName) {
		User user = new User();
		setField(user, "id", UUID.randomUUID());
		user.setEmail(email);
		user.setFullName(fullName);
		return user;
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Não foi possível preparar os dados do teste.", exception);
		}
	}
}
