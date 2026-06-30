package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.domain.WhatsappConversation;
import com.helpdesk.helpdesk.domain.WhatsappConversationStep;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.repository.WhatsappConversationRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class WhatsappWebhookServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private SectorRepository sectorRepository;

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private TicketService ticketService;

	@Mock
	private WhatsappService whatsappService;

	@Mock
	private WhatsappConversationRepository whatsappConversationRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private EmailDomainValidationService emailDomainValidationService;

	@Mock
	private TenantExecutionService tenantExecutionService;

	@Mock
	private ScopedUserLookupService scopedUserLookupService;

	@Mock
	private HttpServletRequest request;

	@Captor
	private ArgumentCaptor<WhatsappConversation> conversationCaptor;

	private WhatsappWebhookService service;

	@BeforeEach
	void setUp() {
		service = new WhatsappWebhookService(
			userRepository,
			roleRepository,
			sectorRepository,
			ticketRepository,
			ticketService,
			whatsappService,
			whatsappConversationRepository,
			passwordEncoder,
			emailDomainValidationService,
			tenantExecutionService,
			scopedUserLookupService
		);

		lenient().when(whatsappConversationRepository.save(any(WhatsappConversation.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(whatsappConversationRepository.saveAndFlush(any(WhatsappConversation.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(whatsappConversationRepository.saveAll(anyList()))
			.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void shouldPromptInitialModeOnFirstInboundMessage() {
		User companyOwner = companyOwner();

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.empty());

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "Oi", List.of());

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.ASK_INITIAL_MODE, conversationCaptor.getValue().getCurrentStep());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("1) Criar chamado"));
	}

	@Test
	void shouldStartExistingTicketFlowWhenUserChoosesCreateTicket() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ASK_INITIAL_MODE);
		Sector sector = new Sector();
		sector.setName("Financeiro");

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));
		when(sectorRepository.findActiveByCreatedByIdOrderByNameAsc(companyOwner.getId()))
			.thenReturn(List.of(sector));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "1", List.of());

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.ASK_SECTOR, conversationCaptor.getValue().getCurrentStep());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("Financeiro"));
	}

	@Test
	void shouldStartNormalConversationWhenUserChoosesOptionTwo() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ASK_INITIAL_MODE);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "2", List.of());

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE, conversationCaptor.getValue().getCurrentStep());
		assertEquals(true, conversationCaptor.getValue().isNormalConversationActive());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("Conversa normal iniciada"));
	}

	@Test
	void shouldAllowOpenTicketCommandDuringNormalConversation() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		conversation.setNormalConversationActive(true);
		Sector sector = new Sector();
		sector.setName("Financeiro");

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));
		when(sectorRepository.findActiveByCreatedByIdOrderByNameAsc(companyOwner.getId()))
			.thenReturn(List.of(sector));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "abrir chamado", List.of());

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.ASK_SECTOR, conversationCaptor.getValue().getCurrentStep());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("Financeiro"));
	}

	@Test
	void shouldReturnToNormalConversationEvenWithTicketModeActive() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ACTIVE_TICKET);
		conversation.setNormalConversationActive(true);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "conversa normal", List.of());

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE, conversationCaptor.getValue().getCurrentStep());
		assertEquals(true, conversationCaptor.getValue().isNormalConversationActive());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("trocar chamado"));
	}

	@Test
	void shouldRestartFlowAfterClosedNormalConversationReceivesNewMessage() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "quero atendimento", List.of());

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.ASK_INITIAL_MODE, conversationCaptor.getValue().getCurrentStep());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("2) Conversa normal"));
	}

	@Test
	void shouldCloseNormalConversationWhenAgentUsesAnyLetterCaseAndNotifyUser() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		conversation.setNormalConversationActive(true);

		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/whatsapp/webhook");
		when(whatsappService.resolveCompanyAdminBySession("empresa-a")).thenReturn(companyOwner);
		doAnswer(invocation -> {
			Runnable runnable = invocation.getArgument(1);
			runnable.run();
			return null;
		}).when(tenantExecutionService).runInTenantByOwnerUserId(eq(companyOwner.getId()), any(Runnable.class));
		when(whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(companyOwner.getId(), "5511999999999@c.us"))
			.thenReturn(Optional.of(conversation));
		conversation.setWhatsappTransportId("5511999999999@c.us");

		service.receive(
			"""
			{
			  "event": "message",
			  "session": "empresa-a",
			  "chat": { "id": "5511999999999@c.us" },
			  "sender": { "id": "5588999999999@c.us" },
			  "body": "Finalizar Conversa",
			  "fromMe": true
			}
			""",
			request
		);

		verify(whatsappConversationRepository, atLeastOnce()).save(conversationCaptor.capture());
		List<WhatsappConversation> savedConversations = conversationCaptor.getAllValues();
		assertEquals(
			WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED,
			savedConversations.get(savedConversations.size() - 1).getCurrentStep()
		);
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999@c.us"), contains("Atendimento encerrado"));
	}

	@Test
	void shouldCloseNormalConversationWhenOutgoingPayloadUsesRemoteJidAndConversationFields() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		conversation.setNormalConversationActive(true);
		conversation.setWhatsappTransportId("5511999999999@c.us");

		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/whatsapp/webhook");
		when(whatsappService.resolveCompanyAdminBySession("empresa-a")).thenReturn(companyOwner);
		doAnswer(invocation -> {
			Runnable runnable = invocation.getArgument(1);
			runnable.run();
			return null;
		}).when(tenantExecutionService).runInTenantByOwnerUserId(eq(companyOwner.getId()), any(Runnable.class));
		when(whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(companyOwner.getId(), "5511999999999@c.us"))
			.thenReturn(Optional.of(conversation));

		service.receive(
			"""
			{
			  "event": "messages.upsert",
			  "session": "empresa-a",
			  "message": {
			    "key": {
			      "remoteJid": "5511999999999@c.us",
			      "fromMe": true
			    },
			    "conversation": "FINALIZAR CONVERSA"
			  }
			}
			""",
			request
		);

		verify(whatsappConversationRepository, atLeastOnce()).save(conversationCaptor.capture());
		List<WhatsappConversation> savedConversations = conversationCaptor.getAllValues();
		assertEquals(
			WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED,
			savedConversations.get(savedConversations.size() - 1).getCurrentStep()
		);
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999@c.us"), contains("Atendimento encerrado"));
	}

	@Test
	void shouldCloseInactiveNormalConversationsAfterTwoDays() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		conversation.setNormalConversationActive(true);
		conversation.setLastInboundMessageAt(OffsetDateTime.now().minusDays(3));

		when(whatsappConversationRepository.findInactiveNormalConversations(any(OffsetDateTime.class)))
			.thenReturn(List.of(conversation));

		int closedCount = service.closeInactiveNormalConversations(OffsetDateTime.now().minusDays(2));

		assertEquals(1, closedCount);
		verify(whatsappConversationRepository).saveAll(anyList());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("encerrado por inatividade"));
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED, conversation.getCurrentStep());
	}

	@Test
	void shouldKeepWhatsappTicketCreatedWhenPostCreationRoutingLookupFails() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ASK_DESCRIPTION);
		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Administrativo");
		conversation.setSector(sector);
		conversation.setPendingName("Kauan Rubem");
		conversation.setPendingEmail("kauanrubem@gmail.com");

		User requester = new User();
		setField(requester, "id", UUID.randomUUID());
		requester.setFullName("Kauan Rubem");
		requester.setEmail("kauanrubem@gmail.com");
		requester.setStatus(UserStatus.ACTIVE);

		User assignee = new User();
		setField(assignee, "id", UUID.randomUUID());
		assignee.setFullName("Silvia Freire");

		Ticket createdTicket = new Ticket();
		setField(createdTicket, "id", UUID.randomUUID());
		createdTicket.setProtocol("CA-2026-0059");
		createdTicket.setAssignedTo(assignee);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("kauanrubem@gmail.com"))
			.thenReturn(Optional.empty());
		when(scopedUserLookupService.findUniqueByPhoneNumberInCurrentTenant("5511999999999"))
			.thenReturn(Optional.of(requester));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(ticketService.createFromWhatsapp(any(TicketService.CreateWhatsappTicketRequest.class)))
			.thenReturn(createdTicket);
		when(ticketRepository.findOpenWhatsappTicketsForRouting(
			eq(companyOwner.getId()),
			eq(requester.getId()),
			eq("kauanrubem@gmail.com"),
			eq("5511999999999"),
			eq("")
		)).thenThrow(new IllegalStateException("Falha ao consultar roteamento"));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "mensagem inicial completa", List.of());

		verify(whatsappConversationRepository).saveAndFlush(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.ACTIVE_TICKET, conversationCaptor.getValue().getCurrentStep());
		assertEquals(createdTicket, conversationCaptor.getValue().getActiveTicket());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("Chamado aberto"));
	}

	@Test
	void shouldCreateNewRequesterInsteadOfReusingResponderEmployeeEmail() {
		User companyOwner = companyOwner();
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ASK_DESCRIPTION);
		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Administrativo");
		conversation.setSector(sector);
		conversation.setPendingName("Kauan Rubem");
		conversation.setPendingEmail("funcionario@empresa.com");

		User responderEmployee = new User();
		setField(responderEmployee, "id", UUID.randomUUID());
		responderEmployee.setFullName("Funcionario Interno");
		responderEmployee.setEmail("funcionario@empresa.com");
		responderEmployee.setStatus(UserStatus.ACTIVE);
		responderEmployee.getRoles().add(role("EMPLOYEE"));
		responderEmployee.setCompanyOwner(companyOwner);

		Role defaultUserRole = role("USER");
		Ticket createdTicket = new Ticket();
		setField(createdTicket, "id", UUID.randomUUID());
		createdTicket.setProtocol("CA-2026-0060");

		ArgumentCaptor<TicketService.CreateWhatsappTicketRequest> requestCaptor =
			ArgumentCaptor.forClass(TicketService.CreateWhatsappTicketRequest.class);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("funcionario@empresa.com"))
			.thenReturn(Optional.of(responderEmployee));
		when(scopedUserLookupService.findUniqueByPhoneNumberInCurrentTenant("5511999999999"))
			.thenReturn(Optional.empty());
		when(roleRepository.findByCode("USER")).thenReturn(Optional.of(defaultUserRole));
		when(passwordEncoder.encode(any(String.class))).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(ticketService.createFromWhatsapp(any(TicketService.CreateWhatsappTicketRequest.class)))
			.thenReturn(createdTicket);
		when(ticketRepository.findOpenWhatsappTicketsForRouting(any(), any(), any(), any(), any()))
			.thenReturn(List.of(createdTicket));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "mensagem inicial completa", List.of());

		verify(ticketService).createFromWhatsapp(requestCaptor.capture());
		User requesterUsed = requestCaptor.getValue().requester();
		assertNotSame(responderEmployee, requesterUsed);
		assertEquals("funcionario@empresa.com", requesterUsed.getEmail());
		assertNull(requesterUsed.getCompanyOwner());
		assertEquals(1, requesterUsed.getRoles().size());
		assertEquals("USER", requesterUsed.getRoles().iterator().next().getCode());
	}

	@Test
	void shouldMergePhoneAndTransportConversationAndKeepActiveTicket() {
		User companyOwner = companyOwner();

		WhatsappConversation phoneConversation = conversation(companyOwner, WhatsappConversationStep.ASK_DESCRIPTION);
		phoneConversation.setPhoneNumber("5511999999999");
		phoneConversation.setPendingName("Kauan Rubem");
		phoneConversation.setPendingEmail("kauanrubem@gmail.com");

		WhatsappConversation transportConversation = conversation(companyOwner, WhatsappConversationStep.ASK_INITIAL_MODE);
		transportConversation.setWhatsappTransportId("5511999999999@s.whatsapp.net");
		transportConversation.setPhoneNumber("551199999999917");

		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Administrativo");
		phoneConversation.setSector(sector);

		User requester = new User();
		setField(requester, "id", UUID.randomUUID());
		requester.setFullName("Kauan Rubem");
		requester.setEmail("kauanrubem@gmail.com");
		requester.setStatus(UserStatus.ACTIVE);

		Ticket createdTicket = new Ticket();
		setField(createdTicket, "id", UUID.randomUUID());
		createdTicket.setProtocol("CA-2026-0061");

		when(whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(
			companyOwner.getId(),
			"5511999999999@s.whatsapp.net"
		)).thenReturn(Optional.of(transportConversation));
		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(phoneConversation));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("kauanrubem@gmail.com"))
			.thenReturn(Optional.empty());
		when(scopedUserLookupService.findUniqueByPhoneNumberInCurrentTenant("5511999999999"))
			.thenReturn(Optional.of(requester));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(ticketService.createFromWhatsapp(any(TicketService.CreateWhatsappTicketRequest.class)))
			.thenReturn(createdTicket);
		when(ticketRepository.findOpenWhatsappTicketsForRouting(any(), any(), any(), any(), any()))
			.thenReturn(List.of(createdTicket));

		service.handleIncomingMessage(
			companyOwner,
			"",
			"5511999999999:17@s.whatsapp.net",
			"mensagem inicial completa",
			List.of()
		);

		verify(whatsappConversationRepository).delete(transportConversation);
		verify(whatsappConversationRepository).saveAndFlush(conversationCaptor.capture());
		assertSame(phoneConversation, conversationCaptor.getValue());
		assertEquals("5511999999999", conversationCaptor.getValue().getPhoneNumber());
		assertEquals("5511999999999@s.whatsapp.net", conversationCaptor.getValue().getWhatsappTransportId());
		assertEquals(WhatsappConversationStep.ACTIVE_TICKET, conversationCaptor.getValue().getCurrentStep());
		assertEquals(createdTicket, conversationCaptor.getValue().getActiveTicket());
	}

	private User companyOwner() {
		User user = new User();
		user.setFullName("Empresa A");
		setField(user, "id", UUID.randomUUID());
		return user;
	}

	private WhatsappConversation conversation(User companyOwner, WhatsappConversationStep step) {
		WhatsappConversation conversation = new WhatsappConversation();
		setField(conversation, "id", UUID.randomUUID());
		conversation.setCompanyOwner(companyOwner);
		conversation.setPhoneNumber("5511999999999");
		conversation.setCurrentStep(step);
		conversation.setNormalConversationActive(step == WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		return conversation;
	}

	private String contains(String value) {
		return org.mockito.ArgumentMatchers.contains(value);
	}

	private Role role(String code) {
		Role role = new Role();
		setField(role, "id", UUID.randomUUID());
		setField(role, "code", code);
		setField(role, "name", code);
		return role;
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
