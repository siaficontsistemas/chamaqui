package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.User;
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
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED, conversationCaptor.getValue().getCurrentStep());
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
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED, conversationCaptor.getValue().getCurrentStep());
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
	void shouldCreateNewRequesterInsteadOfReusingResponderEmployeeEmail() {
		User companyOwner = companyOwner();
		companyOwner.setCompanyType(CompanyType.RESPONDER);
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ASK_DESCRIPTION);
		conversation.setPendingName("Kauan Rubem");
		conversation.setPendingEmail("kauanrubem@gmail.com");
		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Financeiro");
		sector.setCreatedBy(companyOwner);
		conversation.setSector(sector);

		User responderEmployee = new User();
		setField(responderEmployee, "id", UUID.randomUUID());
		responderEmployee.setFullName("Kauan Interno");
		responderEmployee.setEmail("kauanrubem@gmail.com");
		responderEmployee.setCompanyOwner(companyOwner);
		responderEmployee.getRoles().add(role("EMPLOYEE"));

		User savedRequester = new User();
		setField(savedRequester, "id", UUID.randomUUID());
		savedRequester.setFullName("Kauan Rubem");
		savedRequester.setEmail("kauanrubem@gmail.com");
		savedRequester.getRoles().add(role("USER"));

		User assignee = new User();
		setField(assignee, "id", UUID.randomUUID());
		assignee.setFullName("Silvia Freire");

		com.helpdesk.helpdesk.domain.Ticket createdTicket = new com.helpdesk.helpdesk.domain.Ticket();
		setField(createdTicket, "id", UUID.randomUUID());
		createdTicket.setProtocol("CA-2026-0001");
		createdTicket.setAssignedTo(assignee);
		createdTicket.setSector(sector);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("kauanrubem@gmail.com"))
			.thenReturn(Optional.of(responderEmployee));
		when(scopedUserLookupService.findUniqueByPhoneNumberInCurrentTenant("5511999999999"))
			.thenReturn(Optional.empty());
		when(roleRepository.findByCode("USER")).thenReturn(Optional.of(role("USER")));
		when(passwordEncoder.encode(any(String.class))).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenReturn(savedRequester);
		when(ticketService.createFromWhatsapp(any(TicketService.CreateWhatsappTicketRequest.class))).thenReturn(createdTicket);
		when(ticketRepository.findOpenWhatsappTicketsForRouting(
			eq(companyOwner.getId()),
			isNull(),
			eq(savedRequester.getEmail()),
			eq("5511999999999"),
			eq("")
		)).thenReturn(List.of(createdTicket));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "dawdwawdawdaw", List.of());

		verify(userRepository).save(argThat((User user) -> {
			assertNotSame(responderEmployee, user);
			assertEquals("kauanrubem@gmail.com", user.getEmail());
			assertTrue(user.getRoles().stream().anyMatch(role -> "USER".equals(role.getCode())));
			return true;
		}));
		verify(ticketService).createFromWhatsapp(argThat((TicketService.CreateWhatsappTicketRequest request) ->
			request.requester() != responderEmployee
				&& "kauanrubem@gmail.com".equals(request.requester().getEmail())
		));
	}

	@Test
	void shouldKeepWhatsappTicketCreatedWhenRoutingLookupFails() {
		User companyOwner = companyOwner();
		companyOwner.setCompanyType(CompanyType.RESPONDER);
		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ASK_DESCRIPTION);
		conversation.setPendingName("Cliente Externo");
		conversation.setPendingEmail("cliente@gmail.com");
		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Financeiro");
		sector.setCreatedBy(companyOwner);
		conversation.setSector(sector);

		User savedRequester = new User();
		setField(savedRequester, "id", UUID.randomUUID());
		savedRequester.setFullName("Cliente Externo");
		savedRequester.setEmail("cliente@gmail.com");
		savedRequester.getRoles().add(role("USER"));

		User assignee = new User();
		setField(assignee, "id", UUID.randomUUID());
		assignee.setFullName("Silvia Freire");

		com.helpdesk.helpdesk.domain.Ticket createdTicket = new com.helpdesk.helpdesk.domain.Ticket();
		setField(createdTicket, "id", UUID.randomUUID());
		createdTicket.setProtocol("CA-2026-0002");
		createdTicket.setAssignedTo(assignee);
		createdTicket.setSector(sector);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("cliente@gmail.com"))
			.thenReturn(Optional.empty());
		when(scopedUserLookupService.findUniqueByPhoneNumberInCurrentTenant("5511999999999"))
			.thenReturn(Optional.empty());
		when(roleRepository.findByCode("USER")).thenReturn(Optional.of(role("USER")));
		when(passwordEncoder.encode(any(String.class))).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenReturn(savedRequester);
		when(ticketService.createFromWhatsapp(any(TicketService.CreateWhatsappTicketRequest.class))).thenReturn(createdTicket);
		when(ticketRepository.findOpenWhatsappTicketsForRouting(
			eq(companyOwner.getId()),
			isNull(),
			eq(savedRequester.getEmail()),
			eq("5511999999999"),
			eq("")
		)).thenReturn(List.of()).thenReturn(List.of()).thenThrow(new IllegalStateException("falha de consulta"));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "mensagem inicial longa", List.of());

		verify(whatsappConversationRepository).saveAndFlush(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.ACTIVE_TICKET, conversationCaptor.getValue().getCurrentStep());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("Chamado aberto."));
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("CA-2026-0002"));
	}

	@Test
	void shouldMergeDuplicatePhoneAndTransportConversationsBeforeOpeningTicket() {
		User companyOwner = companyOwner();
		companyOwner.setCompanyType(CompanyType.RESPONDER);

		WhatsappConversation phoneConversation = conversation(companyOwner, WhatsappConversationStep.ASK_DESCRIPTION);
		phoneConversation.setPendingName("Cliente Externo");
		phoneConversation.setPendingEmail("cliente@gmail.com");
		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Financeiro");
		sector.setCreatedBy(companyOwner);
		phoneConversation.setSector(sector);

		WhatsappConversation transportConversation = conversation(companyOwner, WhatsappConversationStep.ASK_INITIAL_MODE);
		setField(transportConversation, "id", UUID.randomUUID());
		transportConversation.setPhoneNumber("5511888888888");
		transportConversation.setWhatsappTransportId("5511999999999@c.us");

		User savedRequester = new User();
		setField(savedRequester, "id", UUID.randomUUID());
		savedRequester.setFullName("Cliente Externo");
		savedRequester.setEmail("cliente@gmail.com");
		savedRequester.getRoles().add(role("USER"));

		User assignee = new User();
		setField(assignee, "id", UUID.randomUUID());
		assignee.setFullName("Silvia Freire");

		com.helpdesk.helpdesk.domain.Ticket createdTicket = new com.helpdesk.helpdesk.domain.Ticket();
		setField(createdTicket, "id", UUID.randomUUID());
		createdTicket.setProtocol("CA-2026-0003");
		createdTicket.setAssignedTo(assignee);
		createdTicket.setSector(sector);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(companyOwner.getId(), "5511999999999@c.us"))
			.thenReturn(Optional.of(transportConversation));
		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(phoneConversation));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("cliente@gmail.com"))
			.thenReturn(Optional.empty());
		when(scopedUserLookupService.findUniqueByPhoneNumberInCurrentTenant("5511999999999"))
			.thenReturn(Optional.empty());
		when(scopedUserLookupService.findUniqueByWhatsappTransportIdInCurrentTenant("5511999999999@c.us"))
			.thenReturn(Optional.empty());
		when(roleRepository.findByCode("USER")).thenReturn(Optional.of(role("USER")));
		when(passwordEncoder.encode(any(String.class))).thenReturn("hash");
		when(userRepository.save(any(User.class))).thenReturn(savedRequester);
		when(ticketService.createFromWhatsapp(any(TicketService.CreateWhatsappTicketRequest.class))).thenReturn(createdTicket);
		when(ticketRepository.findOpenWhatsappTicketsForRouting(
			eq(companyOwner.getId()),
			isNull(),
			eq(savedRequester.getEmail()),
			eq("5511999999999"),
			eq("5511999999999@c.us")
		)).thenReturn(List.of(createdTicket));

		service.handleIncomingMessage(
			companyOwner,
			"5511999999999",
			"5511999999999@c.us",
			"mensagem inicial longa",
			List.of()
		);

		verify(whatsappConversationRepository).delete(transportConversation);
		verify(whatsappConversationRepository).saveAndFlush(conversationCaptor.capture());
		WhatsappConversation savedConversation = conversationCaptor.getValue();
		assertEquals(phoneConversation.getId(), savedConversation.getId());
		assertEquals(WhatsappConversationStep.ACTIVE_TICKET, savedConversation.getCurrentStep());
		assertEquals("5511999999999", savedConversation.getPhoneNumber());
		assertEquals("5511999999999@c.us", savedConversation.getWhatsappTransportId());
		assertEquals(createdTicket, savedConversation.getActiveTicket());
	}

	@Test
	void shouldRecoverRecentOpenTicketWhenConversationIsStillAskDescription() {
		User companyOwner = companyOwner();
		companyOwner.setCompanyType(CompanyType.RESPONDER);

		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ASK_DESCRIPTION);
		conversation.setPendingName("Cliente Externo");
		conversation.setPendingEmail("cliente@gmail.com");
		conversation.setLastInboundMessageAt(OffsetDateTime.now().minusMinutes(1));

		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Administrativo");
		sector.setCreatedBy(companyOwner);
		conversation.setSector(sector);

		User requester = new User();
		setField(requester, "id", UUID.randomUUID());
		requester.setFullName("Cliente Externo");
		requester.setEmail("cliente@gmail.com");
		requester.setPhoneNumber("5511999999999");
		requester.getRoles().add(role("USER"));

		User assignee = new User();
		setField(assignee, "id", UUID.randomUUID());
		assignee.setFullName("Silvia Freire");

		com.helpdesk.helpdesk.domain.Ticket openTicket = new com.helpdesk.helpdesk.domain.Ticket();
		setField(openTicket, "id", UUID.randomUUID());
		setField(openTicket, "createdAt", OffsetDateTime.now().minusSeconds(20));
		openTicket.setProtocol("CA-2026-0059");
		openTicket.setRequester(requester);
		openTicket.setAssignedTo(assignee);
		openTicket.setSector(sector);
		openTicket.setStatus(ticketStatus("OPEN"));

		when(whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwner.getId(), "5511999999999"))
			.thenReturn(Optional.of(conversation));
		when(scopedUserLookupService.findUniqueByPhoneNumberInCurrentTenant("5511999999999"))
			.thenReturn(Optional.of(requester));
		when(scopedUserLookupService.findUniqueByEmailInCurrentTenant("cliente@gmail.com"))
			.thenReturn(Optional.of(requester));
		when(ticketRepository.findOpenWhatsappTicketsForRouting(
			eq(companyOwner.getId()),
			eq(requester.getId()),
			eq(requester.getEmail()),
			eq("5511999999999"),
			eq("")
		)).thenReturn(List.of(openTicket));

		service.handleIncomingMessage(companyOwner, "5511999999999", "", "oi", List.of());

		verify(ticketService).addWhatsappMessage(openTicket.getId(), "oi", List.of());
		verify(whatsappConversationRepository, atLeastOnce()).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.ACTIVE_TICKET, conversationCaptor.getValue().getCurrentStep());
		assertEquals(openTicket, conversationCaptor.getValue().getActiveTicket());
	}

	@Test
	void shouldKeepActiveTicketWhenIncomingWhatsappIdContainsDeviceSuffix() {
		User companyOwner = companyOwner();
		companyOwner.setCompanyType(CompanyType.RESPONDER);

		WhatsappConversation conversation = conversation(companyOwner, WhatsappConversationStep.ACTIVE_TICKET);
		conversation.setWhatsappTransportId("5511999999999@s.whatsapp.net");

		User requester = new User();
		setField(requester, "id", UUID.randomUUID());
		requester.setFullName("Cliente Externo");
		requester.setEmail("cliente@gmail.com");
		requester.setPhoneNumber("5511999999999");
		requester.setWhatsappTransportId("5511999999999@s.whatsapp.net");
		requester.getRoles().add(role("USER"));

		Sector sector = new Sector();
		setField(sector, "id", UUID.randomUUID());
		sector.setName("Administrativo");
		sector.setCreatedBy(companyOwner);

		com.helpdesk.helpdesk.domain.Ticket openTicket = new com.helpdesk.helpdesk.domain.Ticket();
		setField(openTicket, "id", UUID.randomUUID());
		openTicket.setProtocol("CA-2026-0060");
		openTicket.setRequester(requester);
		openTicket.setSector(sector);
		openTicket.setStatus(ticketStatus("OPEN"));
		conversation.setActiveTicket(openTicket);

		when(whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(
			companyOwner.getId(),
			"5511999999999@s.whatsapp.net"
		)).thenReturn(Optional.of(conversation));

		service.handleIncomingMessage(
			companyOwner,
			"5511999999999:17@s.whatsapp.net",
			"5511999999999:17@s.whatsapp.net",
			"nova mensagem",
			List.of()
		);

		verify(ticketService).addWhatsappMessage(openTicket.getId(), "nova mensagem", List.of());
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

	private com.helpdesk.helpdesk.domain.TicketStatus ticketStatus(String code) {
		com.helpdesk.helpdesk.domain.TicketStatus status = new com.helpdesk.helpdesk.domain.TicketStatus();
		setField(status, "id", UUID.randomUUID());
		setField(status, "code", code);
		setField(status, "name", code);
		return status;
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
