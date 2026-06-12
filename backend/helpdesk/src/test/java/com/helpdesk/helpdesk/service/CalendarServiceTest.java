package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.helpdesk.helpdesk.domain.CalendarObligation;
import com.helpdesk.helpdesk.domain.CalendarObligationPriority;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.calendar.CalendarObligationResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarTicketSearchResponse;
import com.helpdesk.helpdesk.dto.calendar.CreateCalendarObligationRequest;
import com.helpdesk.helpdesk.dto.calendar.UpdateCalendarObligationRequest;
import com.helpdesk.helpdesk.repository.CalendarObligationRepository;
import com.helpdesk.helpdesk.repository.CalendarReminderNotificationRepository;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

	@Mock
	private CalendarObligationRepository calendarObligationRepository;

	@Mock
	private CalendarReminderNotificationRepository calendarReminderNotificationRepository;

	@Mock
	private CompanyPartnershipRepository companyPartnershipRepository;

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private TenantAccessService tenantAccessService;

	@Mock
	private ScopedUserLookupService scopedUserLookupService;

	@Captor
	private ArgumentCaptor<CalendarObligation> obligationCaptor;

	private CalendarService service;

	@BeforeEach
	void setUp() {
		service = new CalendarService(
			calendarObligationRepository,
			calendarReminderNotificationRepository,
			companyPartnershipRepository,
			ticketRepository,
			userRepository,
			tenantAccessService,
			scopedUserLookupService
		);

		lenient().when(calendarObligationRepository.save(any(CalendarObligation.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void shouldCreateObligationWithoutLinkedTickets() {
		User admin = adminUser();
		User recipient = recipientUser(admin);

		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("admin@empresa.com"))
			.thenReturn(Optional.of(admin));
		when(userRepository.findAllByDocumentNumberOrderByCreatedAtAsc("11122233344"))
			.thenReturn(List.of(recipient));

		CreateCalendarObligationRequest request = new CreateCalendarObligationRequest(
			"Entrega do fechamento",
			"Conferir anexos",
			OffsetDateTime.now().plusDays(2),
			OffsetDateTime.now().plusDays(1),
			"HIGH",
			null,
			null,
			List.of("111.222.333-44"),
			"admin@empresa.com"
		);

		CalendarObligationResponse response = service.create(request);

		verify(calendarObligationRepository).save(obligationCaptor.capture());
		CalendarObligation savedObligation = obligationCaptor.getValue();
		assertTrue(savedObligation.getLinkedTickets().isEmpty());
		assertTrue(response.linkedTickets().isEmpty());
	}

	@Test
	void shouldCreateObligationWithLinkedTicketsDuringCreate() {
		User admin = adminUser();
		User recipient = recipientUser(admin);
		Ticket linkedTicket = ticket("CA-2026-0001", "Chamado 1", "OPEN", "Aberto", recipient);

		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("admin@empresa.com"))
			.thenReturn(Optional.of(admin));
		when(userRepository.findAllByDocumentNumberOrderByCreatedAtAsc("11122233344"))
			.thenReturn(List.of(recipient));
		when(ticketRepository.findDetailedVisibleByIdAndEmail(linkedTicket.getId(), "admin@empresa.com"))
			.thenReturn(Optional.of(linkedTicket));

		CreateCalendarObligationRequest request = new CreateCalendarObligationRequest(
			"Entrega do fechamento",
			"Conferir anexos",
			OffsetDateTime.now().plusDays(2),
			OffsetDateTime.now().plusDays(1),
			"HIGH",
			null,
			List.of(linkedTicket.getId()),
			List.of("111.222.333-44"),
			"admin@empresa.com"
		);

		CalendarObligationResponse response = service.create(request);

		verify(calendarObligationRepository).save(obligationCaptor.capture());
		CalendarObligation savedObligation = obligationCaptor.getValue();
		assertEquals(1, savedObligation.getLinkedTickets().size());
		assertEquals(1, response.linkedTickets().size());
		assertEquals("CA-2026-0001", response.linkedTickets().getFirst().protocol());
	}

	@Test
	void shouldUpdateLinkedTicketsForExistingObligationWithinUpdateFlow() {
		User admin = adminUser();
		User recipient = recipientUser(admin);
		Ticket firstTicket = ticket("CA-2026-0001", "Chamado 1", "OPEN", "Aberto", recipient);
		Ticket secondTicket = ticket("CA-2026-0002", "Chamado 2", "CLOSED", "Fechado", recipient);
		CalendarObligation obligation = obligation(admin, recipient);

		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("admin@empresa.com"))
			.thenReturn(Optional.of(admin));
		when(calendarObligationRepository.findDetailedById(obligation.getId()))
			.thenReturn(Optional.of(obligation));
		when(ticketRepository.findDetailedVisibleByIdAndEmail(firstTicket.getId(), "admin@empresa.com"))
			.thenReturn(Optional.of(firstTicket));
		when(ticketRepository.findDetailedVisibleByIdAndEmail(secondTicket.getId(), "admin@empresa.com"))
			.thenReturn(Optional.of(secondTicket));
		when(userRepository.findAllByDocumentNumberOrderByCreatedAtAsc("11122233344"))
			.thenReturn(List.of(recipient));

		CalendarObligationResponse response = service.update(
			obligation.getId(),
			new UpdateCalendarObligationRequest(
				"Obrigação de teste",
				"Descrição atualizada",
				obligation.getDueAt(),
				null,
				"MEDIUM",
				admin.getId(),
				List.of(firstTicket.getId(), secondTicket.getId()),
				List.of("111.222.333-44"),
				"admin@empresa.com"
			)
		);

		verify(calendarObligationRepository).save(obligationCaptor.capture());
		CalendarObligation savedObligation = obligationCaptor.getValue();
		assertEquals(2, savedObligation.getLinkedTickets().size());
		assertEquals(2, response.linkedTickets().size());
		assertTrue(response.linkedTickets().stream().anyMatch(ticket -> "CA-2026-0001".equals(ticket.protocol())));
		assertTrue(response.linkedTickets().stream().anyMatch(ticket -> "CA-2026-0002".equals(ticket.protocol())));
	}

	@Test
	void shouldKeepLinkedTicketsVisibleWhenTicketStatusChanges() {
		User admin = adminUser();
		User recipient = recipientUser(admin);
		Ticket linkedTicket = ticket("CA-2026-0099", "Obrigação fiscal", "CLOSED", "Fechado", recipient);
		CalendarObligation obligation = obligation(admin, recipient);
		obligation.setLinkedTickets(Set.of(linkedTicket));

		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("admin@empresa.com"))
			.thenReturn(Optional.of(admin));
		when(calendarObligationRepository.findVisibleByCompanyOwnerIdOrderByDueAtAsc(admin.getId()))
			.thenReturn(List.of(obligation));

		List<CalendarObligationResponse> responses = service.listVisible("admin@empresa.com");

		assertEquals(1, responses.size());
		assertEquals(1, responses.getFirst().linkedTickets().size());
		assertEquals("CLOSED", responses.getFirst().linkedTickets().getFirst().statusCode());
		assertEquals("Fechado", responses.getFirst().linkedTickets().getFirst().statusName());
	}

	@Test
	void shouldSearchTicketsWithHasMoreFlag() {
		User admin = adminUser();
		Ticket firstTicket = ticket("CA-2026-0001", "Chamado 1", "OPEN", "Aberto", admin);
		Ticket secondTicket = ticket("CA-2026-0002", "Chamado 2", "CLOSED", "Fechado", admin);

		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("admin@empresa.com"))
			.thenReturn(Optional.of(admin));
		when(ticketRepository.searchVisibleByEmail(eq("admin@empresa.com"), eq("financeiro"), any(Pageable.class)))
			.thenReturn(List.of(firstTicket, secondTicket));

		CalendarTicketSearchResponse response = service.searchTickets("admin@empresa.com", "financeiro", 0, 1);

		assertEquals(1, response.tickets().size());
		assertTrue(response.hasMore());
		assertEquals("CA-2026-0001", response.tickets().getFirst().protocol());
	}

	private CalendarObligation obligation(User admin, User recipient) {
		CalendarObligation obligation = new CalendarObligation();
		ReflectionTestUtils.setField(obligation, "id", UUID.randomUUID());
		obligation.setCompanyOwner(admin);
		obligation.setCreatedBy(admin);
		obligation.setLinkedCompanyOwner(admin);
		obligation.setRecipients(Set.of(recipient));
		obligation.setTitle("Obrigação de teste");
		obligation.setPriority(CalendarObligationPriority.MEDIUM);
		obligation.setDueAt(OffsetDateTime.now().plusDays(1));
		ReflectionTestUtils.setField(obligation, "createdAt", OffsetDateTime.now().minusDays(1));
		ReflectionTestUtils.setField(obligation, "updatedAt", OffsetDateTime.now());
		return obligation;
	}

	private User adminUser() {
		User admin = new User();
		ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
		admin.setEmail("admin@empresa.com");
		admin.setFullName("Admin");
		admin.setCompanyName("Empresa");
		admin.setStatus(UserStatus.ACTIVE);
		admin.getRoles().add(role("ADMIN"));
		return admin;
	}

	private User recipientUser(User admin) {
		User recipient = new User();
		ReflectionTestUtils.setField(recipient, "id", UUID.randomUUID());
		recipient.setEmail("funcionario@empresa.com");
		recipient.setFullName("Funcionario");
		recipient.setDocumentNumber("11122233344");
		recipient.setCompanyOwner(admin);
		recipient.setStatus(UserStatus.ACTIVE);
		recipient.getRoles().add(role("EMPLOYEE"));
		return recipient;
	}

	private Ticket ticket(String protocol, String title, String statusCode, String statusName, User responsible) {
		Ticket ticket = new Ticket();
		ReflectionTestUtils.setField(ticket, "id", UUID.randomUUID());
		ticket.setProtocol(protocol);
		ticket.setTitle(title);
		ticket.setAssignedTo(responsible);
		ticket.setRequester(responsible);
		ticket.setStatus(ticketStatus(statusCode, statusName));
		ReflectionTestUtils.setField(ticket, "createdAt", OffsetDateTime.now().minusDays(2));
		ReflectionTestUtils.setField(ticket, "updatedAt", OffsetDateTime.now());
		assertNotNull(ticket.getId());
		return ticket;
	}

	private TicketStatus ticketStatus(String code, String name) {
		TicketStatus ticketStatus = new TicketStatus();
		ReflectionTestUtils.setField(ticketStatus, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(ticketStatus, "code", code);
		ReflectionTestUtils.setField(ticketStatus, "name", name);
		return ticketStatus;
	}

	private Role role(String code) {
		Role role = new Role();
		ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(role, "code", code);
		ReflectionTestUtils.setField(role, "name", code);
		return role;
	}
}
