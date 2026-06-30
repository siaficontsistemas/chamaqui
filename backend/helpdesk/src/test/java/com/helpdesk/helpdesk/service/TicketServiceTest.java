package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketChannel;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.TicketReplyNotification;
import com.helpdesk.helpdesk.domain.TicketStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketMessageRequest;
import com.helpdesk.helpdesk.dto.ticket.TicketMessageResponse;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.SectorMemberRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TicketAssignmentNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketAttachmentRepository;
import com.helpdesk.helpdesk.repository.TicketClosureNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketMessageRepository;
import com.helpdesk.helpdesk.repository.TicketPriorityRepository;
import com.helpdesk.helpdesk.repository.TicketReplyNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.TicketStatusRepository;
import com.helpdesk.helpdesk.repository.TicketTransferNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.repository.WhatsappConversationRepository;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private CompanyPartnershipRepository companyPartnershipRepository;

	@Mock
	private SectorMemberRepository sectorMemberRepository;

	@Mock
	private SectorRepository sectorRepository;

	@Mock
	private TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository;

	@Mock
	private TicketStatusRepository ticketStatusRepository;

	@Mock
	private TicketPriorityRepository ticketPriorityRepository;

	@Mock
	private TicketMessageRepository ticketMessageRepository;

	@Mock
	private TicketAttachmentRepository ticketAttachmentRepository;

	@Mock
	private TicketTransferNotificationRepository ticketTransferNotificationRepository;

	@Mock
	private TicketClosureNotificationRepository ticketClosureNotificationRepository;

	@Mock
	private TicketReplyNotificationRepository ticketReplyNotificationRepository;

	@Mock
	private TicketAttachmentStorageService ticketAttachmentStorageService;

	@Mock
	private TicketClosureEmailService ticketClosureEmailService;

	@Mock
	private WhatsappService whatsappService;

	@Mock
	private WhatsappConversationRepository whatsappConversationRepository;

	@Mock
	private TenantAccessService tenantAccessService;

	@Mock
	private ScopedUserLookupService scopedUserLookupService;

	@Mock
	private AuditTrailService auditTrailService;

	@InjectMocks
	private TicketService ticketService;

	@Test
	void shouldMirrorEmployeeMessageToWhatsappForResponderCompany() {
		User responderAdmin = user("admin@empresa.com", "Empresa Admin", "ADMIN", null);
		User employee = user("funcionario@empresa.com", "Funcionario", "EMPLOYEE", responderAdmin);
		User requester = user("cliente@cliente.com", "Cliente", "USER", null);

		Sector sector = new Sector();
		sector.setName("Financeiro");
		sector.setSlug("financeiro");
		sector.setCreatedBy(responderAdmin);

		TicketStatus openStatus = ticketStatus("OPEN");
		TicketStatus inProgressStatus = ticketStatus("IN_PROGRESS");

		Ticket ticket = new Ticket();
		UUID ticketId = UUID.randomUUID();
		setField(ticket, "id", ticketId);
		ticket.setProtocol("CA-2026-0007");
		ticket.setTitle("Chamado WhatsApp");
		ticket.setDescription("Mensagem inicial");
		ticket.setRequester(requester);
		ticket.setSector(sector);
		ticket.setStatus(openStatus);
		ticket.setChannel(TicketChannel.WHATSAPP);

		TicketMessage savedMessage = new TicketMessage();
		setField(savedMessage, "id", UUID.randomUUID());
		savedMessage.setTicket(ticket);
		savedMessage.setAuthor(employee);
		savedMessage.setMessage("Retorno do funcionario");
		savedMessage.setInternal(false);
		savedMessage.setCreatedAt(OffsetDateTime.now());

		TicketMessage initialMessage = new TicketMessage();
		setField(initialMessage, "id", UUID.randomUUID());
		initialMessage.setTicket(ticket);
		initialMessage.setAuthor(requester);
		initialMessage.setMessage("Mensagem inicial");
		initialMessage.setInternal(false);
		initialMessage.setCreatedAt(OffsetDateTime.now().minusMinutes(5));

		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("funcionario@empresa.com"))
			.thenReturn(Optional.of(employee));
		when(ticketRepository.findDetailedVisibleByIdAndEmail(ticketId, "funcionario@empresa.com"))
			.thenReturn(Optional.of(ticket));
		when(ticketMessageRepository.existsByTicketId(ticketId)).thenReturn(true);
		when(ticketMessageRepository.findFirstByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(Optional.of(initialMessage));
		when(ticketMessageRepository.save(any(TicketMessage.class))).thenReturn(savedMessage);
		when(ticketAttachmentRepository.findByMessageIdOrderByCreatedAtAsc(savedMessage.getId())).thenReturn(List.of());
		when(ticketStatusRepository.findByCode("IN_PROGRESS")).thenReturn(Optional.of(inProgressStatus));
		when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(whatsappConversationRepository.findByActiveTicketId(ticketId))
			.thenReturn(Optional.of(conversation(responderAdmin, ticket)));

		TicketMessageResponse response = ticketService.addMessage(
			ticketId,
			new CreateTicketMessageRequest("funcionario@empresa.com", "Retorno do funcionario"),
			List.of()
		);

		verify(whatsappService).sendMessage(
			eq(responderAdmin),
			eq("5511999999999@c.us"),
			eq("*Funcionario diz para o protocolo CA-2026-0007:* Retorno do funcionario"),
			eq(List.of())
		);
		assertEquals("Funcionario", response.authorName());
		assertEquals("Funcionário", response.authorRole());
		assertEquals("IN_PROGRESS", ticket.getStatus().getCode());
	}

	@Test
	void shouldHideReplyNotificationWhenResponderAdminRepliesEvenIfRequesterBelongsToSameCompany() {
		User responderAdmin = user("admin@empresa.com", "Empresa Admin", "ADMIN", null);
		User requesterEmployee = user("cliente@empresa.com", "Cliente", "EMPLOYEE", responderAdmin);

		Sector sector = new Sector();
		sector.setName("Financeiro");
		sector.setSlug("financeiro");
		sector.setCreatedBy(responderAdmin);

		TicketStatus openStatus = ticketStatus("OPEN");
		TicketStatus inProgressStatus = ticketStatus("IN_PROGRESS");

		Ticket ticket = new Ticket();
		UUID ticketId = UUID.randomUUID();
		setField(ticket, "id", ticketId);
		ticket.setProtocol("CA-2026-0008");
		ticket.setTitle("Chamado interno");
		ticket.setDescription("Mensagem inicial");
		ticket.setRequester(requesterEmployee);
		ticket.setSector(sector);
		ticket.setStatus(openStatus);
		ticket.setChannel(TicketChannel.PORTAL);

		TicketMessage initialMessage = new TicketMessage();
		setField(initialMessage, "id", UUID.randomUUID());
		initialMessage.setTicket(ticket);
		initialMessage.setAuthor(requesterEmployee);
		initialMessage.setMessage("Mensagem inicial");
		initialMessage.setInternal(false);
		initialMessage.setCreatedAt(OffsetDateTime.now().minusMinutes(5));

		TicketMessage savedMessage = new TicketMessage();
		setField(savedMessage, "id", UUID.randomUUID());
		savedMessage.setTicket(ticket);
		savedMessage.setAuthor(responderAdmin);
		savedMessage.setMessage("Resposta do administrador");
		savedMessage.setInternal(false);
		savedMessage.setCreatedAt(OffsetDateTime.now());

		TicketReplyNotification existingNotification = new TicketReplyNotification();
		setField(existingNotification, "id", UUID.randomUUID());
		existingNotification.setTicket(ticket);
		existingNotification.setMessage(initialMessage);
		existingNotification.setRecipient(responderAdmin);
		existingNotification.setHidden(false);

		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("admin@empresa.com"))
			.thenReturn(Optional.of(responderAdmin));
		when(ticketRepository.findDetailedVisibleByIdAndEmail(ticketId, "admin@empresa.com"))
			.thenReturn(Optional.of(ticket));
		when(ticketMessageRepository.existsByTicketId(ticketId)).thenReturn(true);
		when(ticketMessageRepository.findFirstByTicketIdOrderByCreatedAtAsc(ticketId)).thenReturn(Optional.of(initialMessage));
		when(ticketMessageRepository.save(any(TicketMessage.class))).thenReturn(savedMessage);
		when(ticketStatusRepository.findByCode("IN_PROGRESS")).thenReturn(Optional.of(inProgressStatus));
		when(ticketReplyNotificationRepository.findByTicketId(ticketId)).thenReturn(List.of(existingNotification));
		when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ticketService.addMessage(
			ticketId,
			new CreateTicketMessageRequest("admin@empresa.com", "Resposta do administrador"),
			List.of()
		);

		verify(ticketReplyNotificationRepository).saveAll(
			argThat((List<TicketReplyNotification> notifications) ->
				notifications.size() == 1 && notifications.get(0).isHidden()
			)
		);
		verify(ticketReplyNotificationRepository, never()).save(any(TicketReplyNotification.class));
	}

	@Test
	void shouldCreateWhatsappTicketAssignedToCompanyAdminWhenSectorHasNoEmployees() {
		User responderAdmin = user("admin@empresa.com", "Empresa Admin", "ADMIN", null);
		responderAdmin.setCompanyType(CompanyType.RESPONDER);
		User requester = user("cliente@gmail.com", "Cliente Externo", "USER", null);

		Sector sector = new Sector();
		UUID sectorId = UUID.randomUUID();
		setField(sector, "id", sectorId);
		sector.setName("Financeiro");
		sector.setSlug("financeiro");
		sector.setCreatedBy(responderAdmin);

		TicketStatus openStatus = ticketStatus("OPEN");
		com.helpdesk.helpdesk.domain.TicketPriority mediumPriority = ticketPriority("MEDIUM");

		when(ticketStatusRepository.findByCode("OPEN")).thenReturn(Optional.of(openStatus));
		when(ticketPriorityRepository.findByCode("MEDIUM")).thenReturn(Optional.of(mediumPriority));
		when(sectorRepository.findById(sectorId)).thenReturn(Optional.of(sector));
		when(tenantAccessService.getCurrentTenantOwnerUserId()).thenReturn(Optional.of(responderAdmin.getId()));
		when(ticketRepository.findMaxProtocolSequenceByPrefix(anyString())).thenReturn(0L);
		when(sectorMemberRepository.findBySectorIdOrderByAssignedAtAsc(sectorId)).thenReturn(List.of());
		when(ticketRepository.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
			Ticket savedTicket = invocation.getArgument(0);
			setField(savedTicket, "id", UUID.randomUUID());
			return savedTicket;
		});
		when(ticketMessageRepository.existsByTicketId(any(UUID.class))).thenReturn(false);
		when(ticketMessageRepository.save(any(TicketMessage.class))).thenAnswer(invocation -> {
			TicketMessage message = invocation.getArgument(0);
			setField(message, "id", UUID.randomUUID());
			return message;
		});

		Ticket createdTicket = ticketService.createFromWhatsapp(
			new TicketService.CreateWhatsappTicketRequest(
				requester,
				"5511999999999",
				"5511999999999@c.us",
				responderAdmin.getId(),
				sectorId,
				null,
				"Mensagem inicial do cliente pelo WhatsApp",
				List.of()
			)
		);

		assertEquals(responderAdmin.getId(), createdTicket.getAssignedTo().getId());
		assertEquals(TicketChannel.WHATSAPP, createdTicket.getChannel());
	}

	private User user(String email, String fullName, String roleCode, User companyOwner) {
		User user = new User();
		setField(user, "id", UUID.randomUUID());
		user.setEmail(email);
		user.setFullName(fullName);
		user.setCompanyOwner(companyOwner);
		user.getRoles().add(role(roleCode));
		return user;
	}

	private Role role(String code) {
		Role role = new Role();
		setField(role, "id", UUID.randomUUID());
		setField(role, "code", code);
		setField(role, "name", code);
		return role;
	}

	private com.helpdesk.helpdesk.domain.TicketPriority ticketPriority(String code) {
		com.helpdesk.helpdesk.domain.TicketPriority priority = new com.helpdesk.helpdesk.domain.TicketPriority();
		setField(priority, "id", UUID.randomUUID());
		setField(priority, "code", code);
		setField(priority, "name", code);
		return priority;
	}

	private TicketStatus ticketStatus(String code) {
		TicketStatus status = new TicketStatus();
		setField(status, "id", UUID.randomUUID());
		setField(status, "code", code);
		setField(status, "name", code);
		return status;
	}

	private com.helpdesk.helpdesk.domain.WhatsappConversation conversation(User companyOwner, Ticket ticket) {
		com.helpdesk.helpdesk.domain.WhatsappConversation conversation =
			new com.helpdesk.helpdesk.domain.WhatsappConversation();
		setField(conversation, "id", UUID.randomUUID());
		conversation.setCompanyOwner(companyOwner);
		conversation.setPhoneNumber("5511999999999");
		conversation.setWhatsappTransportId("5511999999999@c.us");
		conversation.setActiveTicket(ticket);
		return conversation;
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Nao foi possivel preparar os dados do teste.", exception);
		}
	}
}
