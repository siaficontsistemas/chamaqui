package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Captor;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("sem registro de chamado oficial"));
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

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED, conversationCaptor.getValue().getCurrentStep());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("Seu atendimento foi finalizado"));
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

		verify(whatsappConversationRepository).save(conversationCaptor.capture());
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED, conversationCaptor.getValue().getCurrentStep());
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999@c.us"), contains("Seu atendimento foi finalizado"));
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
		verify(whatsappService).sendMessage(eq(companyOwner), eq("5511999999999"), contains("finalizado por inatividade"));
		assertEquals(WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED, conversation.getCurrentStep());
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
