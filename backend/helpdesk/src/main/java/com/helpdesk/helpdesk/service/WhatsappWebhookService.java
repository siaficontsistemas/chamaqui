package com.helpdesk.helpdesk.service;

import java.io.IOException;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.domain.WhatsappConversation;
import com.helpdesk.helpdesk.domain.WhatsappConversationStep;
import com.helpdesk.helpdesk.dto.ticket.TicketTargetAssigneeResponse;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.repository.WhatsappConversationRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class WhatsappWebhookService {

	private static final Logger logger = LoggerFactory.getLogger(WhatsappWebhookService.class);
	private static final long INACTIVITY_ROUTING_WINDOW_HOURS = 2;
	private static final long AUTOMATED_OUTBOUND_CACHE_MINUTES = 5;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final SectorRepository sectorRepository;
	private final TicketRepository ticketRepository;
	private final TicketService ticketService;
	private final WhatsappService whatsappService;
	private final WhatsappConversationRepository whatsappConversationRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailDomainValidationService emailDomainValidationService;
	private final TenantExecutionService tenantExecutionService;
	private final ScopedUserLookupService scopedUserLookupService;
	private final ConcurrentMap<String, OffsetDateTime> automatedOutboundMessageCache = new ConcurrentHashMap<>();

	public WhatsappWebhookService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		SectorRepository sectorRepository,
		TicketRepository ticketRepository,
		TicketService ticketService,
		WhatsappService whatsappService,
		WhatsappConversationRepository whatsappConversationRepository,
		PasswordEncoder passwordEncoder,
		EmailDomainValidationService emailDomainValidationService,
		TenantExecutionService tenantExecutionService,
		ScopedUserLookupService scopedUserLookupService
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.sectorRepository = sectorRepository;
		this.ticketRepository = ticketRepository;
		this.ticketService = ticketService;
		this.whatsappService = whatsappService;
		this.whatsappConversationRepository = whatsappConversationRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailDomainValidationService = emailDomainValidationService;
		this.tenantExecutionService = tenantExecutionService;
		this.scopedUserLookupService = scopedUserLookupService;
	}

	public void receive(String payload, HttpServletRequest request) {
		JsonNode payloadJson = parsePayload(payload);
		String event = firstNonBlank(extractText(payloadJson, "event"), extractDeepText(payloadJson, "event"));
		String sessionName = resolveSessionName(payloadJson);
		boolean fromMe = resolveFromMe(payloadJson);
		String transportId = resolveIncomingTransportId(payloadJson, fromMe);
		String phone = resolveIncomingPhone(payloadJson, transportId, fromMe);
		String body = resolveIncomingBody(payloadJson);
		List<TicketService.IncomingAttachment> attachments = resolveIncomingAttachments(payloadJson);
		boolean groupMessage = isGroupMessage(payloadJson);
		boolean automaticOutboundMessage = fromMe && matchesRecentAutomatedOutbound(sessionName, firstNonBlank(phone, transportId), body);

		logger.info(
			"Webhook WhatsApp recebido: method={}, path={}, session={}, event={}, phone={}, transportId={}, fromMe={}, groupMessage={}, attachments={}, bodyPreview={}",
			request.getMethod(),
			request.getRequestURI(),
			sessionName,
			event,
			phone,
			transportId,
			fromMe,
			groupMessage,
			attachments.size(),
			preview(body)
		);

		if (fromMe && !groupMessage) {
			logger.info(
				"Webhook WhatsApp fromMe debug: session={}, event={}, phone={}, transportId={}, automatic={}, isMessageEvent={}, isFinishConversationCommand={}, bodyPreview={}, rawPayloadPreview={}",
				sessionName,
				event,
				phone,
				transportId,
				automaticOutboundMessage,
				isMessageEvent(event),
				isFinishConversationCommand(body),
				preview(body),
				previewRawPayload(payload)
			);
		}

		if (isMessageEvent(event)
			&& fromMe
			&& !groupMessage
			&& !automaticOutboundMessage
			&& !sessionName.isBlank()
			&& (!phone.isBlank() || !transportId.isBlank())) {
			handleOutgoingConversationActivity(sessionName, firstNonBlank(phone, transportId), transportId);
		}

		if (isMessageEvent(event)
			&& fromMe
			&& !groupMessage
			&& !automaticOutboundMessage
			&& !sessionName.isBlank()
			&& (!phone.isBlank() || !transportId.isBlank())
			&& isFinishConversationCommand(body)) {
			handleOutgoingConversationClosureCommand(sessionName, firstNonBlank(phone, transportId), transportId);
			return;
		}

		if (!isMessageEvent(event)
			|| payloadJson == null
			|| fromMe
			|| groupMessage
			|| sessionName.isBlank()
			|| (phone.isBlank() && transportId.isBlank())) {
			return;
		}

		try {
			User companyOwner = whatsappService.resolveCompanyAdminBySession(sessionName);
			tenantExecutionService.runInTenantByOwnerUserId(
				companyOwner.getId(),
				() -> handleIncomingMessage(companyOwner, firstNonBlank(phone, transportId), transportId, body, attachments)
			);
		} catch (IllegalArgumentException exception) {
			logger.warn("Webhook do WhatsApp ignorado por sessão inválida: session={}, reason={}", sessionName, exception.getMessage());
		} catch (Exception exception) {
			logger.error(
				"Falha ao processar webhook do WhatsApp: session={}, phone={}, transportId={}, event={}",
				sessionName,
				phone,
				transportId,
				event,
				exception
			);
		}
	}

	@Transactional
	void handleIncomingMessage(
		User companyOwner,
		String phoneNumber,
		String incomingTransportId,
		String body,
		List<TicketService.IncomingAttachment> attachments
	) {
		String whatsappTransportId = normalizeWhatsappTransportId(incomingTransportId);
		String normalizedProvidedPhone = normalizePhone(phoneNumber);
		String normalizedPhone = normalizedProvidedPhone.isBlank()
			? normalizePhone(incomingTransportId)
			: normalizedProvidedPhone;
		String normalizedBody = normalizeInboundMessage(body);
		List<TicketService.IncomingAttachment> incomingAttachments = attachments == null ? List.of() : attachments;

		WhatsappConversation conversation = resolveConversation(companyOwner.getId(), normalizedPhone, whatsappTransportId)
			.orElseGet(() -> {
				WhatsappConversation createdConversation = new WhatsappConversation();
				createdConversation.setCompanyOwner(companyOwner);
				createdConversation.setPhoneNumber(normalizedPhone);
				createdConversation.setCurrentStep(WhatsappConversationStep.ASK_INITIAL_MODE);
				return createdConversation;
			});

		conversation.setCompanyOwner(companyOwner);
		conversation.setPhoneNumber(normalizedPhone);
		conversation.setWhatsappTransportId(whatsappTransportId);
		OffsetDateTime previousInboundMessageAt = conversation.getLastInboundMessageAt();
		OffsetDateTime previousOutboundMessageAt = conversation.getLastOutboundMessageAt();
		conversation.setLastInboundMessageAt(OffsetDateTime.now());
		if (conversation.getCurrentStep() == null) {
			conversation.setCurrentStep(WhatsappConversationStep.ASK_INITIAL_MODE);
		}

		String replyTarget = resolveReplyTarget(conversation, normalizedPhone);
		boolean isNewConversation = conversation.getId() == null;

		if (isNewConversation) {
			promptForInitialMode(companyOwner, conversation, replyTarget, null);
			return;
		}

		if (shouldPromptForInactivityDestination(
			conversation,
			previousInboundMessageAt,
			previousOutboundMessageAt,
			normalizedBody,
			incomingAttachments
		)) {
			promptForInactivityMessageDestination(companyOwner, conversation, replyTarget, normalizedBody, incomingAttachments);
			return;
		}

		if (conversation.getCurrentStep() == WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED) {
			promptForInitialMode(companyOwner, conversation, replyTarget, null);
			return;
		}

		if (conversation.getCurrentStep() == WhatsappConversationStep.ASK_INACTIVITY_MESSAGE_DESTINATION) {
			handleInactivityMessageDestinationStep(companyOwner, conversation, replyTarget, normalizedBody);
			return;
		}

		if (conversation.isNormalConversationActive()
			&& conversation.getCurrentStep() != WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE
			&& conversation.getCurrentStep() != WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED
			&& conversation.getCurrentStep() != WhatsappConversationStep.ASK_INITIAL_MODE
			&& isNormalConversationSelection(normalizedBody)) {
			startNormalConversation(
				companyOwner,
				conversation,
				replyTarget,
				"Tudo bem. Vamos continuar por aqui em conversa normal, sem vincular as próximas mensagens a um chamado."
			);
			return;
		}

		if (conversation.getCurrentStep() == WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE) {
			if (isTicketModeSelection(normalizedBody)) {
				startNewTicketFlow(companyOwner, conversation, replyTarget, "Vamos abrir um novo chamado.");
				return;
			}
			if (isSwitchTicketCommand(normalizedBody)) {
				handleOpenTicketSelectionFromNormalConversation(companyOwner, conversation, replyTarget);
				return;
			}
			whatsappConversationRepository.save(conversation);
			return;
		}	

		if (conversation.getCurrentStep() == WhatsappConversationStep.ASK_INITIAL_MODE) {
			handleInitialModeStep(companyOwner, conversation, replyTarget, normalizedBody);
			return;
		}

		if (isCancelCommand(normalizedBody) && isNewTicketCreationStep(conversation.getCurrentStep())) {
			cancelNewTicketFlow(companyOwner, conversation, replyTarget);
			return;
		}

		if (handleOpenTicketRouting(companyOwner, conversation, replyTarget, normalizedBody, incomingAttachments)) {
			return;
		}

		if (hasActiveOpenTicket(conversation)) {
			if (isOpenNewTicketCommand(normalizedBody)) {
				startNewTicketFlow(companyOwner, conversation, replyTarget, "Vamos abrir um novo chamado.");
				return;
			}
			if (isRestartCommand(normalizedBody)) {
				replyWithMessage(
					companyOwner,
					replyTarget,
					"Seu atendimento já está em andamento e não pode ser reiniciado agora. Aguarde o encerramento pelo funcionário."
				);
				return;
			}
			if (!normalizedBody.isBlank() || !incomingAttachments.isEmpty()) {
				ticketService.addWhatsappMessage(conversation.getActiveTicket().getId(), normalizedBody, incomingAttachments);
			}
			return;
		}

		if (isRestartCommand(normalizedBody) || isOpenNewTicketCommand(normalizedBody)) {
			startNewTicketFlow(companyOwner, conversation, replyTarget, "Atendimento reiniciado.");
			return;
		}

		boolean hadClosedTicket = conversation.getActiveTicket() != null;
		if (hadClosedTicket) {
			startNewTicketFlow(companyOwner, conversation, replyTarget, null);
			return;
		}

		if (isGreetingMessage(normalizedBody)) {
			promptForInitialMode(companyOwner, conversation, replyTarget, null);
			return;
		}

		switch (conversation.getCurrentStep()) {
			case ASK_INITIAL_MODE -> handleInitialModeStep(companyOwner, conversation, replyTarget, normalizedBody);
			case ASK_INACTIVITY_MESSAGE_DESTINATION ->
				handleInactivityMessageDestinationStep(companyOwner, conversation, replyTarget, normalizedBody);
			case NORMAL_CONVERSATION_ACTIVE, NORMAL_CONVERSATION_CLOSED -> whatsappConversationRepository.save(conversation);
			case ASK_REUSE_REQUESTER_DATA -> handleReuseRequesterDataStep(companyOwner, conversation, replyTarget, normalizedBody);
			case ASK_ACTIVE_TICKET_SELECTION ->
				startNewTicketFlow(companyOwner, conversation, replyTarget, "Não encontrei mais chamados abertos para continuar.");
			case ASK_SECTOR -> handleSectorStep(companyOwner, conversation, replyTarget, normalizedBody);
			case ASK_ASSIGNEE -> handleAssigneeStep(companyOwner, conversation, replyTarget, normalizedBody);
			case ASK_NAME -> handleNameStep(companyOwner, conversation, replyTarget, normalizedBody);
			case ASK_EMAIL -> handleEmailStep(companyOwner, conversation, replyTarget, normalizedBody);
			case ASK_DOCUMENT -> handleDocumentStep(companyOwner, conversation, replyTarget);
			case ASK_SUBJECT, ASK_DESCRIPTION ->
				handleDescriptionStep(companyOwner, conversation, replyTarget, normalizedBody, incomingAttachments);
			case ACTIVE_TICKET -> {
				startNewTicketFlow(companyOwner, conversation, replyTarget, "Vamos abrir um novo chamado.");
			}
		}
	}

	private void handleOutgoingConversationClosureCommand(String sessionName, String phoneNumber, String incomingTransportId) {
		try {
			User companyOwner = whatsappService.resolveCompanyAdminBySession(sessionName);
			tenantExecutionService.runInTenantByOwnerUserId(
				companyOwner.getId(),
				() -> closeNormalConversationByAgent(companyOwner, phoneNumber, incomingTransportId)
			);
		} catch (Exception exception) {
			logger.error(
				"Falha ao encerrar conversa normal por comando do atendente: session={}, phone={}, transportId={}",
				sessionName,
				phoneNumber,
				incomingTransportId,
				exception
			);
		}
	}

	@Transactional
	void closeNormalConversationByAgent(User companyOwner, String phoneNumber, String incomingTransportId) {
		String whatsappTransportId = normalizeWhatsappTransportId(incomingTransportId);
		String normalizedPhone = normalizePhone(phoneNumber);
		resolveConversation(companyOwner.getId(), normalizedPhone, whatsappTransportId)
			.filter(WhatsappConversation::isNormalConversationActive)
			.ifPresent(conversation -> {
				String replyTarget = resolveReplyTarget(conversation, normalizedPhone);
				closeNormalConversation(conversation);
				whatsappConversationRepository.save(conversation);
				replyWithMessage(companyOwner, replyTarget, buildNormalConversationClosedMessage(false));
			});
	}

	private void handleOutgoingConversationActivity(String sessionName, String phoneNumber, String incomingTransportId) {
		try {
			User companyOwner = whatsappService.resolveCompanyAdminBySession(sessionName);
			tenantExecutionService.runInTenantByOwnerUserId(
				companyOwner.getId(),
				() -> touchConversationOutbound(companyOwner, phoneNumber, incomingTransportId)
			);
		} catch (Exception exception) {
			logger.error(
				"Falha ao atualizar atividade de saída do WhatsApp: session={}, phone={}, transportId={}",
				sessionName,
				phoneNumber,
				incomingTransportId,
				exception
			);
		}
	}

	@Transactional
	void touchConversationOutbound(User companyOwner, String phoneNumber, String incomingTransportId) {
		String whatsappTransportId = normalizeWhatsappTransportId(incomingTransportId);
		String normalizedPhone = normalizePhone(phoneNumber);
		resolveConversation(companyOwner.getId(), normalizedPhone, whatsappTransportId)
			.ifPresent(conversation -> {
				conversation.setLastOutboundMessageAt(OffsetDateTime.now());
				whatsappConversationRepository.save(conversation);
			});
	}

	@Transactional
	public int closeInactiveNormalConversations(OffsetDateTime inactiveSince) {
		List<WhatsappConversation> conversations = whatsappConversationRepository.findInactiveNormalConversations(inactiveSince);
		for (WhatsappConversation conversation : conversations) {
			String replyTarget = resolveReplyTarget(conversation, normalizePhone(conversation.getPhoneNumber()));
			closeNormalConversation(conversation);
			replyWithMessage(conversation.getCompanyOwner(), replyTarget, buildNormalConversationClosedMessage(true));
		}
		if (!conversations.isEmpty()) {
			whatsappConversationRepository.saveAll(conversations);
		}
		return conversations.size();
	}

	private boolean shouldPromptForInactivityDestination(
		WhatsappConversation conversation,
		OffsetDateTime previousInboundMessageAt,
		OffsetDateTime previousOutboundMessageAt,
		String body,
		List<TicketService.IncomingAttachment> attachments
	) {
		if (conversation.getCurrentStep() == WhatsappConversationStep.ASK_INITIAL_MODE
			|| conversation.getCurrentStep() == WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED
			|| conversation.getCurrentStep() == WhatsappConversationStep.ASK_INACTIVITY_MESSAGE_DESTINATION
			|| isNewTicketCreationStep(conversation.getCurrentStep())
			|| (body.isBlank() && (attachments == null || attachments.isEmpty()))) {
			return false;
		}

		OffsetDateTime lastInteractionAt = maxTimestamp(previousInboundMessageAt, previousOutboundMessageAt);
		return lastInteractionAt != null && lastInteractionAt.isBefore(OffsetDateTime.now().minusHours(INACTIVITY_ROUTING_WINDOW_HOURS));
	}

	private void promptForInactivityMessageDestination(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String messageBody,
		List<TicketService.IncomingAttachment> attachments
	) {
		List<Ticket> openTickets = loadOpenTicketsForConversation(companyOwner, conversation);
		if (openTickets.isEmpty()) {
			whatsappConversationRepository.save(conversation);
			return;
		}

		conversation.setPendingResumeMessage(messageBody);
		conversation.setPendingResumeAttachments(serializePendingAttachments(attachments));
		conversation.setCurrentStep(WhatsappConversationStep.ASK_INACTIVITY_MESSAGE_DESTINATION);
		conversation.setActiveTicket(null);
		conversation.setLastTicketSelectionPromptAt(OffsetDateTime.now());
		whatsappConversationRepository.save(conversation);
		replyWithMessage(companyOwner, replyTarget, buildInactivityDestinationPrompt(openTickets));
	}

	private void handleInactivityMessageDestinationStep(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body
	) {
		List<Ticket> openTickets = loadOpenTicketsForConversation(companyOwner, conversation);
		if (openTickets.isEmpty()) {
			clearPendingResumeState(conversation);
			startNewTicketFlow(companyOwner, conversation, replyTarget, "Não encontrei chamados abertos. Vamos abrir um novo chamado.");
			return;
		}

		if (isTicketModeSelection(body)) {
			startNewTicketFlow(companyOwner, conversation, replyTarget, "Vamos abrir um novo chamado com sua última mensagem.");
			return;
		}

		Ticket selectedTicket = resolveInactivityTicketSelection(body, openTickets);
		if (selectedTicket == null) {
			replyWithMessage(
				companyOwner,
				replyTarget,
				buildInactivityDestinationPrompt(openTickets)
			);
			return;
		}

		List<TicketService.IncomingAttachment> pendingAttachments = deserializePendingAttachments(conversation.getPendingResumeAttachments());
		String pendingMessage = firstNonBlank(conversation.getPendingResumeMessage(), "");

		clearPendingResumeState(conversation);
		conversation.setActiveTicket(selectedTicket);
		conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
		conversation.setLastTicketSelectionPromptAt(null);
		whatsappConversationRepository.save(conversation);

		if (!pendingMessage.isBlank() || !pendingAttachments.isEmpty()) {
			ticketService.addWhatsappMessage(selectedTicket.getId(), pendingMessage, pendingAttachments);
		}

		replyWithMessage(
			companyOwner,
			replyTarget,
			"Mensagem enviada para o chamado *%s*.".formatted(selectedTicket.getProtocol())
		);
	}

	private String buildNormalConversationClosedMessage(boolean closedByInactivity) {
		if (closedByInactivity) {
			return "Atendimento encerrado por inatividade. Se precisar, envie nova mensagem.";
		}

		return "Atendimento encerrado. Se precisar, envie nova mensagem.";
	}

	private boolean handleOpenTicketRouting(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body,
		List<TicketService.IncomingAttachment> attachments
	) {
		List<Ticket> openTickets;
		try {
			openTickets = loadOpenTicketsForConversation(companyOwner, conversation);
		} catch (RuntimeException exception) {
			logger.warn(
				"Falha ao carregar chamados abertos para roteamento no WhatsApp: companyOwnerId={}, phoneNumber={}, transportId={}, step={}",
				companyOwner.getId(),
				conversation.getPhoneNumber(),
				conversation.getWhatsappTransportId(),
				conversation.getCurrentStep(),
				exception
			);
			return false;
		}
		if (openTickets.isEmpty()) {
			if (isSwitchTicketCommand(body) && canInterruptForTicketSwitch(conversation.getCurrentStep())) {
				replyWithMessage(
					companyOwner,
					replyTarget,
					"Você não possui outros chamados abertos para trocar agora. Podemos continuar este novo chamado."
				);
				return true;
			}
			return false;
		}

		if (isNewTicketCreationStep(conversation.getCurrentStep())) {
			if (isSwitchTicketCommand(body) && canInterruptForTicketSwitch(conversation.getCurrentStep())) {
				discardInProgressNewTicketData(conversation);
				if (openTickets.size() == 1) {
					Ticket selectedTicket = openTickets.get(0);
					conversation.setActiveTicket(selectedTicket);
					conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
					conversation.setLastTicketSelectionPromptAt(null);
					whatsappConversationRepository.save(conversation);
					replyWithMessage(
						companyOwner,
						replyTarget,
						"Vou usar o chamado *%s*. Para abrir outro, envie *abrir novo chamado*."
							.formatted(selectedTicket.getProtocol())
					);
					return true;
				}

				promptForActiveTicketSelection(
					companyOwner,
					conversation,
					replyTarget,
					openTickets,
					"Sem problema. Escolha qual chamado você quer continuar:"
				);
				return true;
			}
			return false;
		}

		if (openTickets.size() == 1) {
			if (conversation.getActiveTicket() == null
				&& conversation.getCurrentStep() != WhatsappConversationStep.ACTIVE_TICKET
				&& conversation.getCurrentStep() != WhatsappConversationStep.ASK_ACTIVE_TICKET_SELECTION) {
				return false;
			}

			Ticket selectedTicket = openTickets.get(0);
			conversation.setActiveTicket(selectedTicket);
			conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
			conversation.setLastTicketSelectionPromptAt(null);

			if (isOpenNewTicketCommand(body)) {
				startNewTicketFlow(companyOwner, conversation, replyTarget, "Vamos abrir um novo chamado.");
				return true;
			}
			if (isRestartCommand(body)) {
				replyWithMessage(
					companyOwner,
					replyTarget,
					"Seu atendimento já está em andamento."
				);
				return true;
			}
			if (!body.isBlank() || !attachments.isEmpty()) {
				whatsappConversationRepository.save(conversation);
				ticketService.addWhatsappMessage(selectedTicket.getId(), body, attachments);
				return true;
			}

			whatsappConversationRepository.save(conversation);
			return true;
		}

		if (isOpenNewTicketCommand(body)) {
			startNewTicketFlow(companyOwner, conversation, replyTarget, "Vamos abrir um novo chamado.");
			return true;
		}
		if (isRestartCommand(body)) {
			promptForActiveTicketSelection(companyOwner, conversation, replyTarget, openTickets, null);
			return true;
		}
		if (isSwitchTicketCommand(body)) {
			promptForActiveTicketSelection(
				companyOwner,
				conversation,
				replyTarget,
				openTickets,
				"Sem problema. Escolha qual chamado você quer continuar:"
			);
			return true;
		}

		Ticket selectedTicket = resolveSelectedOpenTicket(conversation, openTickets);
		if (conversation.getCurrentStep() == WhatsappConversationStep.ASK_ACTIVE_TICKET_SELECTION || selectedTicket == null) {
			return handleActiveTicketSelectionStep(companyOwner, conversation, replyTarget, body, openTickets);
		}

		conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
		conversation.setLastTicketSelectionPromptAt(null);
		whatsappConversationRepository.save(conversation);

		if (!body.isBlank() || !attachments.isEmpty()) {
			ticketService.addWhatsappMessage(selectedTicket.getId(), body, attachments);
		}
		return true;
	}

	private boolean handleActiveTicketSelectionStep(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body,
		List<Ticket> openTickets
	) {
		Ticket selectedTicket = resolveTicketSelection(body, openTickets);
		if (selectedTicket == null) {
			promptForActiveTicketSelection(
				companyOwner,
				conversation,
				replyTarget,
				openTickets,
				"Escolha um chamado válido respondendo com o número ou protocolo:"
			);
			return true;
		}

		conversation.setActiveTicket(selectedTicket);
		conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
		conversation.setLastTicketSelectionPromptAt(null);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			(conversation.isNormalConversationActive()
				? """
				Vou usar o chamado *%s*.
				Para trocar, envie *trocar chamado*.
				Para voltar, envie *conversa normal*.
				"""
				: """
				Vou usar o chamado *%s*.
				Para trocar, envie *trocar chamado*.
				""").formatted(selectedTicket.getProtocol()).trim()
		);
		return true;
	}

	private Ticket resolveInactivityTicketSelection(String body, List<Ticket> openTickets) {
		String normalizedBody = normalizeComparable(body);
		if (normalizedBody.isBlank() || normalizedBody.equals("1") || isTicketModeSelection(body)) {
			return null;
		}

		try {
			int position = Integer.parseInt(normalizedBody);
			if (position >= 2 && position <= openTickets.size() + 1) {
				return openTickets.get(position - 2);
			}
		} catch (NumberFormatException ignored) {
		}

		return resolveTicketSelection(body, openTickets);
	}

	private void promptForActiveTicketSelection(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		List<Ticket> openTickets,
		String prefix
	) {
		conversation.setCurrentStep(WhatsappConversationStep.ASK_ACTIVE_TICKET_SELECTION);
		conversation.setLastTicketSelectionPromptAt(OffsetDateTime.now());
		whatsappConversationRepository.save(conversation);
		replyWithMessage(companyOwner, replyTarget, buildOpenTicketSelectionPrompt(prefix, openTickets));
	}

	private void handleInitialModeStep(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body
	) {
		if (isTicketModeSelection(body)) {
			startNewTicketFlow(companyOwner, conversation, replyTarget, null);
			return;
		}

		if (isNormalConversationSelection(body)) {
			startNormalConversation(companyOwner, conversation, replyTarget);
			return;
		}

		promptForInitialMode(companyOwner, conversation, replyTarget, "Escolha uma opção válida para continuar:");
	}

	private void handleOpenTicketSelectionFromNormalConversation(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget
	) {
		List<Ticket> openTickets = loadOpenTicketsForConversation(companyOwner, conversation);
		if (openTickets.isEmpty()) {
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Você não possui chamado aberto para continuar agora. Se quiser, envie *criar chamado* para abrir um novo."
			);
			return;
		}

		if (openTickets.size() == 1) {
			Ticket selectedTicket = openTickets.get(0);
			conversation.setActiveTicket(selectedTicket);
			conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
			conversation.setLastTicketSelectionPromptAt(null);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Vou usar o chamado *%s*. Para voltar, envie *conversa normal*."
					.formatted(selectedTicket.getProtocol())
			);
			return;
		}

		promptForActiveTicketSelection(
			companyOwner,
			conversation,
			replyTarget,
			openTickets,
			"Escolha qual chamado aberto você quer continuar agora:"
		);
	}

	private void promptForInitialMode(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String prefix
	) {
		prepareForInitialMode(conversation);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(companyOwner, replyTarget, buildInitialModePrompt(prefix));
	}

	private void startNormalConversation(User companyOwner, WhatsappConversation conversation, String replyTarget) {
		startNormalConversation(companyOwner, conversation, replyTarget, null);
	}

	private void startNormalConversation(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String prefix
	) {
		Ticket lastActiveTicket = conversation.getActiveTicket();
		conversation.setNormalConversationActive(true);
		conversation.setCurrentStep(WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		conversation.setSector(null);
		conversation.setPendingMessage(null);
		conversation.setPendingName(null);
		conversation.setPendingEmail(null);
		conversation.setPendingSubject(null);
		conversation.setPendingDocument(null);
		conversation.setPendingAssignedUserId(null);
		clearPendingResumeState(conversation);
		conversation.setActiveTicket(lastActiveTicket);
		conversation.setLastTicketSelectionPromptAt(null);
		whatsappConversationRepository.save(conversation);
		String baseMessage = """
			Conversa normal iniciada.
			Para usar um chamado aberto, envie *trocar chamado*.
			Para abrir um novo, envie *criar chamado*.
			""".trim();
		replyWithMessage(companyOwner, replyTarget, prefix == null || prefix.isBlank() ? baseMessage : prefix.trim() + "\n" + baseMessage);
	}

	private void prepareForInitialMode(WhatsappConversation conversation) {
		conversation.setNormalConversationActive(false);
		conversation.setCurrentStep(WhatsappConversationStep.ASK_INITIAL_MODE);
		conversation.setSector(null);
		conversation.setPendingMessage(null);
		conversation.setPendingName(null);
		conversation.setPendingEmail(null);
		conversation.setPendingDocument(null);
		conversation.setPendingAssignedUserId(null);
		conversation.setPendingSubject(null);
		clearPendingResumeState(conversation);
		conversation.setActiveTicket(null);
		conversation.setLastTicketSelectionPromptAt(null);
	}

	private void closeNormalConversation(WhatsappConversation conversation) {
		conversation.setNormalConversationActive(false);
		conversation.setCurrentStep(WhatsappConversationStep.NORMAL_CONVERSATION_CLOSED);
		conversation.setSector(null);
		conversation.setPendingMessage(null);
		conversation.setPendingName(null);
		conversation.setPendingEmail(null);
		conversation.setPendingDocument(null);
		conversation.setPendingAssignedUserId(null);
		conversation.setPendingSubject(null);
		clearPendingResumeState(conversation);
		conversation.setActiveTicket(null);
		conversation.setLastTicketSelectionPromptAt(null);
	}

	private List<Ticket> loadOpenTicketsForConversation(User companyOwner, WhatsappConversation conversation) {
		User requesterByPhone = resolveExistingRequester(conversation.getPhoneNumber(), conversation.getWhatsappTransportId())
			.orElse(null);
		String pendingEmail = normalizeContactEmail(conversation.getPendingEmail());
		User requesterByEmail = pendingEmail.isBlank()
			? null
			: scopedUserLookupService.findUniqueByEmailInCurrentTenant(pendingEmail)
				.filter(user -> !isResponderSideUser(user))
				.orElse(null);
		User requesterFromActiveTicket = conversation.getActiveTicket() == null
			? null
			: conversation.getActiveTicket().getRequester();
		if (isResponderSideUser(requesterFromActiveTicket)) {
			requesterFromActiveTicket = null;
		}

		UUID requesterId = requesterFromActiveTicket != null
			? requesterFromActiveTicket.getId()
			: requesterByPhone != null
				? requesterByPhone.getId()
				: requesterByEmail == null ? null : requesterByEmail.getId();
		String email = firstNonBlank(
			requesterFromActiveTicket == null ? null : requesterFromActiveTicket.getEmail(),
			requesterByPhone == null ? null : requesterByPhone.getEmail(),
			requesterByEmail == null ? null : requesterByEmail.getEmail(),
			pendingEmail
		);

		return ticketRepository.findOpenWhatsappTicketsForRouting(
			companyOwner.getId(),
			requesterId,
			email,
			normalizePhone(conversation.getPhoneNumber()),
			normalizeWhatsappTransportId(conversation.getWhatsappTransportId())
		).stream()
			.sorted(Comparator.comparing(Ticket::getCreatedAt).reversed())
			.toList();
	}

	private Ticket resolveSelectedOpenTicket(WhatsappConversation conversation, List<Ticket> openTickets) {
		if (conversation.getActiveTicket() == null || conversation.getActiveTicket().getId() == null) {
			return null;
		}

		UUID activeTicketId = conversation.getActiveTicket().getId();
		return openTickets.stream()
			.filter(ticket -> ticket.getId().equals(activeTicketId))
			.findFirst()
			.orElse(null);
	}

	private Ticket resolveTicketSelection(String body, List<Ticket> openTickets) {
		String normalizedBody = normalizeComparable(body);
		if (normalizedBody.isBlank()) {
			return null;
		}

		try {
			int position = Integer.parseInt(normalizedBody);
			if (position >= 1 && position <= openTickets.size()) {
				return openTickets.get(position - 1);
			}
		} catch (NumberFormatException ignored) {
		}

		return openTickets.stream()
			.filter(ticket -> normalizeComparable(ticket.getProtocol()).equals(normalizedBody))
			.findFirst()
			.orElse(null);
	}

	private String buildOpenTicketSelectionPrompt(String prefix, List<Ticket> openTickets) {
		StringBuilder builder = new StringBuilder();
		if (prefix != null && !prefix.isBlank()) {
			builder.append(prefix.trim());
		} else {
			builder.append("Você possui mais de um chamado em aberto. Escolha para qual chamado deseja enviar a próxima mensagem:");
		}

		for (int index = 0; index < openTickets.size(); index++) {
			Ticket ticket = openTickets.get(index);
			builder.append("\n");
			builder.append(index + 1);
			builder.append(". ");
			builder.append(ticket.getProtocol());
			builder.append(" - ");
			builder.append(trimTicketSummary(ticket.getTitle()));
		}

		builder.append("\n\nResponda com o número ou protocolo do chamado.");
		builder.append("\nSe quiser trocar depois, envie *trocar chamado*.");
		return builder.toString();
	}

	private String buildInactivityDestinationPrompt(List<Ticket> openTickets) {
		StringBuilder builder = new StringBuilder();
		builder.append("Sua última mensagem chegou após 2 horas sem interação.");
		builder.append("\nEscolha o destino:");
		builder.append("\n1. Abrir novo chamado");

		for (int index = 0; index < openTickets.size(); index++) {
			Ticket ticket = openTickets.get(index);
			builder.append("\n");
			builder.append(index + 2);
			builder.append(". ");
			builder.append(ticket.getProtocol());
			builder.append(" - ");
			builder.append(trimTicketSummary(ticket.getTitle()));
		}

		builder.append("\n\nResponda com o número desejado.");
		return builder.toString();
	}

	private String buildInitialModePrompt(String prefix) {
		String basePrompt = """
			Olá! Escolha uma opção:
			1) Criar chamado
			2) Conversa normal
			""".trim();
		if (prefix == null || prefix.isBlank()) {
			return basePrompt;
		}
		return prefix.trim() + "\n" + basePrompt;
	}

	private String trimTicketSummary(String value) {
		String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
		if (normalized.length() <= 80) {
			return normalized;
		}
		return normalized.substring(0, 80) + "...";
	}

	private void startNewTicketFlow(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String prefix
	) {
		conversation.setSector(null);
		conversation.setPendingMessage(null);
		conversation.setPendingSubject(null);
		conversation.setPendingDocument(null);
		conversation.setPendingAssignedUserId(null);
		conversation.setLastTicketSelectionPromptAt(null);

		if (prepareReusableRequesterData(conversation)) {
			conversation.setActiveTicket(null);
			conversation.setCurrentStep(WhatsappConversationStep.ASK_REUSE_REQUESTER_DATA);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(companyOwner, replyTarget, buildReuseRequesterPrompt(prefix, conversation));
			return;
		}

		conversation.setActiveTicket(null);
		conversation.setPendingName(null);
		conversation.setPendingEmail(null);
		conversation.setCurrentStep(WhatsappConversationStep.ASK_SECTOR);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(companyOwner, replyTarget, buildSectorPrompt(companyOwner, resolveSectorPromptPrefix(prefix)));
	}

	private void cancelNewTicketFlow(User companyOwner, WhatsappConversation conversation, String replyTarget) {
		discardInProgressNewTicketData(conversation);
		clearPendingResumeState(conversation);
		conversation.setLastTicketSelectionPromptAt(null);
		conversation.setActiveTicket(null);
		if (conversation.isNormalConversationActive()) {
			conversation.setCurrentStep(WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		} else {
			resetConversation(conversation);
		}
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			conversation.isNormalConversationActive()
				? """
				Chamado cancelado.
				A conversa normal continua ativa.
				Para abrir outro, envie *criar chamado*.
				""".trim()
				: """
				Chamado cancelado.
				Para abrir outro, envie *abrir novo chamado*.
				""".trim()
		);
	}

	private void handleReuseRequesterDataStep(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body
	) {
		if (isAffirmativeAnswer(body)) {
			conversation.setSector(null);
			conversation.setPendingSubject(null);
			conversation.setCurrentStep(WhatsappConversationStep.ASK_SECTOR);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				buildSectorPrompt(companyOwner, "Perfeito. Vamos continuar com os mesmos dados. Escolha um setor:")
			);
			return;
		}

		if (isNegativeAnswer(body)) {
			conversation.setSector(null);
			conversation.setPendingName(null);
			conversation.setPendingEmail(null);
			conversation.setPendingDocument(null);
			conversation.setPendingSubject(null);
			conversation.setCurrentStep(WhatsappConversationStep.ASK_SECTOR);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				buildSectorPrompt(companyOwner, "Tudo bem. Vamos cadastrar novos dados. Escolha um setor:")
			);
			return;
		}

		replyWithMessage(companyOwner, replyTarget, "Responda com *sim* para reutilizar os dados ou *não* para informar tudo novamente.");
	}

	private boolean prepareReusableRequesterData(WhatsappConversation conversation) {
		User previousRequester = conversation.getActiveTicket() == null ? null : conversation.getActiveTicket().getRequester();

		String reusableName = normalizePersonName(firstNonBlank(
			previousRequester == null ? null : previousRequester.getFullName(),
			conversation.getPendingName()
		));
		String reusableEmail = normalizeContactEmail(firstNonBlank(
			previousRequester == null ? null : previousRequester.getEmail(),
			conversation.getPendingEmail()
		));

		if (!isValidPersonName(reusableName) || reusableEmail.isBlank()) {
			return false;
		}

		conversation.setPendingName(reusableName);
		conversation.setPendingEmail(reusableEmail);
		conversation.setPendingDocument(null);
		return true;
	}

	private boolean hasReusableRequesterData(WhatsappConversation conversation) {
		return isValidPersonName(normalizePersonName(conversation.getPendingName()))
			&& !normalizeContactEmail(conversation.getPendingEmail()).isBlank();
	}

	private boolean hasPendingResumeContent(WhatsappConversation conversation) {
		return !firstNonBlank(conversation.getPendingResumeMessage(), "").isBlank()
			|| !firstNonBlank(conversation.getPendingResumeAttachments(), "").isBlank();
	}

	private String buildReuseRequesterPrompt(String prefix, WhatsappConversation conversation) {
		StringBuilder builder = new StringBuilder();
		if (prefix != null && !prefix.isBlank()) {
			builder.append(prefix.trim());
			builder.append("\n");
		}
		builder.append("Usar os dados do último chamado?");
		builder.append("\nNome: ").append(conversation.getPendingName());
		builder.append("\nEmail: ").append(conversation.getPendingEmail());
		builder.append("\n1. *Sim*");
		builder.append("\n2. *Não*");
		return builder.toString();
	}

	private String resolveSectorPromptPrefix(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return null;
		}
		return prefix.trim() + " Escolha um setor para continuar:";
	}

	private boolean isAffirmativeAnswer(String value) {
		String normalized = normalizeComparable(value);
		return normalized.equals("sim") || normalized.equals("s") || normalized.equals("1");
	}

	private boolean isNegativeAnswer(String value) {
		String normalized = normalizeComparable(value);
		return normalized.equals("nao") || normalized.equals("não") || normalized.equals("n") || normalized.equals("2");
	}

	private void handleSectorStep(User companyOwner, WhatsappConversation conversation, String replyTarget, String body) {
		List<Sector> sectors = loadCompanySectors(companyOwner);
		Sector selectedSector = resolveSectorSelection(body, sectors);

		if (selectedSector == null) {
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				buildSectorPrompt(companyOwner, "Setor inválido. Responda com o número ou nome de uma das opções:")
			);
			return;
		}

		conversation.setSector(selectedSector);
		conversation.setPendingAssignedUserId(null);
		conversation.setCurrentStep(WhatsappConversationStep.ASK_ASSIGNEE);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			buildAssigneePrompt(
				companyOwner,
				selectedSector,
				"Perfeito. Voce escolheu o setor *%s*. Agora selecione quem deve receber o chamado:".formatted(
					selectedSector.getName()
				)
			)
		);
	}

	private void handleAssigneeStep(User companyOwner, WhatsappConversation conversation, String replyTarget, String body) {
		if (conversation.getSector() == null) {
			resetConversation(conversation);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(companyOwner, replyTarget, buildSectorPrompt(companyOwner, "Escolha o setor antes de continuar:"));
			return;
		}

		List<TicketTargetAssigneeResponse> assignees = ticketService.listAvailableAssigneesForSector(
			conversation.getSector().getId(),
			companyOwner.getId()
		);
		AssigneeSelection selection = resolveAssigneeSelection(body, assignees);

		if (!selection.valid()) {
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				buildAssigneePrompt(
					companyOwner,
					conversation.getSector(),
					"Escolha uma opção válida respondendo com o número, nome do funcionário ou *aleatoriamente*:"
				)
			);
			return;
		}

		conversation.setPendingAssignedUserId(selection.assignedToUserId());
		if (hasReusableRequesterData(conversation)) {
			if (hasPendingResumeContent(conversation)) {
				conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
				whatsappConversationRepository.save(conversation);
				continueTicketCreationFromPendingResume(companyOwner, conversation, replyTarget);
				return;
			}
			conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				"""
				Chamado para %s.
				Nome: *%s*
				Email: *%s*
				Envie a *primeira mensagem*.
				Se desistir, envie *cancelar*.
				""".formatted(
					describeAssigneeSelection(selection.assignedToUserId(), assignees),
					conversation.getPendingName(),
					conversation.getPendingEmail()
				).trim()
			);
			return;
		}

		conversation.setCurrentStep(WhatsappConversationStep.ASK_NAME);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			"""
			Chamado para %s.
			Informe seu *nome completo*.
			Se desistir, envie *cancelar*.
			""".formatted(describeAssigneeSelection(selection.assignedToUserId(), assignees)).trim()
		);
	}

	private void handleNameStep(User companyOwner, WhatsappConversation conversation, String replyTarget, String body) {
		String normalizedName = normalizePersonName(body);
		if (!isValidPersonName(normalizedName)) {
			replyWithMessage(companyOwner, replyTarget, "Nome inválido. Informe seu *nome completo* (nome e sobrenome).");
			return;
		}

		conversation.setPendingName(normalizedName);
		conversation.setCurrentStep(WhatsappConversationStep.ASK_EMAIL);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(companyOwner, replyTarget, "Agora me informe seu *email*. Se desistir, envie *cancelar*.");
	}

	private void handleEmailStep(User companyOwner, WhatsappConversation conversation, String replyTarget, String body) {
		String normalizedEmail = normalizeContactEmail(body);
		if (normalizedEmail.isBlank()) {
			replyWithMessage(companyOwner, replyTarget, "E-mail inválido. Envie no formato *nome@dominio.com*.");
			return;
		}
		try {
			emailDomainValidationService.ensurePublicEmailDomainExists(normalizedEmail);
		} catch (IllegalArgumentException exception) {
			replyWithMessage(companyOwner, replyTarget, "Informe um *email real*, com domínio existente, para continuarmos.");
			return;
		}

		conversation.setPendingEmail(normalizedEmail);
		conversation.setPendingDocument(null);
		if (hasPendingResumeContent(conversation)) {
			conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
			whatsappConversationRepository.save(conversation);
			continueTicketCreationFromPendingResume(companyOwner, conversation, replyTarget);
			return;
		}
		conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			"Perfeito. Agora envie a *primeira mensagem* do seu chamado. Se desistir, envie *cancelar*."
		);
	}

	private void handleDocumentStep(User companyOwner, WhatsappConversation conversation, String replyTarget) {
		if (hasPendingResumeContent(conversation)) {
			conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
			whatsappConversationRepository.save(conversation);
			continueTicketCreationFromPendingResume(companyOwner, conversation, replyTarget);
			return;
		}
		conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			"Não precisamos mais do CPF. Agora envie a *primeira mensagem* do seu chamado. Se desistir, envie *cancelar*."
		);
	}

	private void handleDescriptionStep(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body,
		List<TicketService.IncomingAttachment> attachments
	) {
		handleDescriptionStep(companyOwner, conversation, replyTarget, body, attachments, false);
	}

	private void handleDescriptionStep(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body,
		List<TicketService.IncomingAttachment> attachments,
		boolean allowShortDescription
	) {
		if (conversation.getSector() == null) {
			resetConversation(conversation);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(companyOwner, replyTarget, buildSectorPrompt(companyOwner, "Escolha o setor antes de enviar a descrição:"));
			return;
		}

		if (body.isBlank() && (attachments == null || attachments.isEmpty())) {
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Envie uma descrição do problema para eu criar o chamado no setor *" + conversation.getSector().getName() + "*."
			);
			return;
		}

		String normalizedDescription = body.trim();
		if (normalizedDescription.isBlank() && attachments != null && !attachments.isEmpty()) {
			normalizedDescription = "Arquivo enviado pelo WhatsApp.";
		}
		if (!allowShortDescription && normalizedDescription.length() < 10) {
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Envie a *primeira mensagem* com pelo menos 10 caracteres para eu abrir o chamado."
			);
			return;
		}

		String pendingName = normalizePersonName(conversation.getPendingName());
		String pendingEmail = normalizeContactEmail(conversation.getPendingEmail());
		if (!isValidPersonName(pendingName)) {
			conversation.setPendingName("");
			conversation.setCurrentStep(WhatsappConversationStep.ASK_NAME);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(companyOwner, replyTarget, "Não consegui validar seu nome. Informe novamente seu *nome completo*.");
			return;
		}
		if (pendingEmail.isBlank()) {
			resetConversation(conversation);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Não consegui recuperar seus dados de cadastro. Vamos recomeçar.\n" + buildSectorPrompt(companyOwner, "Escolha um setor:")
			);
			return;
		}

		conversation.setPendingMessage(normalizedDescription);
		boolean keepNormalConversation = conversation.isNormalConversationActive();
		User requester;
		Ticket createdTicket;
		try {
			requester = resolveOrCreateRequester(
				conversation.getPhoneNumber(),
				conversation.getWhatsappTransportId(),
				pendingName,
				pendingEmail
			);
			createdTicket = ticketService.createFromWhatsapp(
				new TicketService.CreateWhatsappTicketRequest(
					requester,
					conversation.getPhoneNumber(),
					conversation.getWhatsappTransportId(),
					companyOwner.getId(),
					conversation.getSector().getId(),
					conversation.getPendingAssignedUserId(),
					normalizedDescription,
					attachments == null ? List.of() : attachments
				)
			);
		} catch (RuntimeException exception) {
			logger.error(
				"Falha ao criar chamado do WhatsApp: companyOwnerId={}, phoneNumber={}, transportId={}",
				companyOwner.getId(),
				conversation.getPhoneNumber(),
				conversation.getWhatsappTransportId(),
				exception
			);
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Não consegui abrir seu chamado agora. Envie novamente a *primeira mensagem* para tentarmos de novo."
			);
			clearPendingResumeState(conversation);
			conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
			whatsappConversationRepository.save(conversation);
			return;
		}

		conversation.setPendingName(requester.getFullName());
		conversation.setPendingEmail(requester.getEmail());
		conversation.setPendingDocument(requester.getDocumentNumber());
		conversation.setPendingMessage(null);
		clearPendingResumeState(conversation);
		conversation.setLastTicketSelectionPromptAt(null);
		if (keepNormalConversation) {
			conversation.setActiveTicket(null);
			conversation.setCurrentStep(WhatsappConversationStep.NORMAL_CONVERSATION_ACTIVE);
		} else {
			conversation.setActiveTicket(createdTicket);
			conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
		}
		whatsappConversationRepository.saveAndFlush(conversation);

		String multipleOpenTicketsGuidance = "";
		try {
			List<Ticket> openTickets = loadOpenTicketsForConversation(companyOwner, conversation);
			if (openTickets.size() >= 2) {
				multipleOpenTicketsGuidance = """

				Como você possui mais de um chamado em aberto, as próximas mensagens que você enviar serão para o último chamado que você criou.
				Se quiser trocar de chamado, envie *trocar chamado*.
				""".trim();
			}
		} catch (RuntimeException exception) {
			logger.warn(
				"Chamado do WhatsApp criado, mas falhou ao carregar a orientação pós-criação: ticketId={}, companyOwnerId={}",
				createdTicket.getId(),
				companyOwner.getId(),
				exception
			);
		}

		replyWithMessage(
			companyOwner,
			replyTarget,
			(keepNormalConversation
				? """
				Chamado aberto.
				Protocolo: %s
				Setor: %s
				Destinatário: %s
				A conversa normal continua ativa.
				Para usar um chamado, envie *trocar chamado*.
				Para abrir outro, envie *abrir novo chamado*.
				%s
				"""
				: """
				Chamado aberto.
				Protocolo: %s
				Setor: %s
				Destinatário: %s
				Para abrir outro, envie *abrir novo chamado*.
				%s
				""").formatted(
					createdTicket.getProtocol(),
					conversation.getSector().getName(),
					createdTicket.getAssignedTo() == null ? "Não informado" : createdTicket.getAssignedTo().getFullName(),
					multipleOpenTicketsGuidance.isBlank() ? "" : "\n" + multipleOpenTicketsGuidance
				).trim()
		);
	}

	private void continueTicketCreationFromPendingResume(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget
	) {
		String pendingMessage = firstNonBlank(conversation.getPendingResumeMessage(), "");
		List<TicketService.IncomingAttachment> pendingAttachments =
			deserializePendingAttachments(conversation.getPendingResumeAttachments());

		if (pendingMessage.isBlank() && pendingAttachments.isEmpty()) {
			clearPendingResumeState(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Não consegui recuperar sua última mensagem. Envie a primeira mensagem do chamado."
			);
			return;
		}

		if (shouldAskForFreshTicketDescription(pendingMessage, pendingAttachments)) {
			clearPendingResumeState(conversation);
			conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				"Agora envie a *primeira mensagem* do seu chamado para eu continuar."
			);
			return;
		}

		replyWithMessage(companyOwner, replyTarget, "Vou usar sua última mensagem para abrir o novo chamado.");
		handleDescriptionStep(companyOwner, conversation, replyTarget, pendingMessage, pendingAttachments, true);
	}

	private boolean shouldAskForFreshTicketDescription(
		String pendingMessage,
		List<TicketService.IncomingAttachment> pendingAttachments
	) {
		if (pendingAttachments != null && !pendingAttachments.isEmpty()) {
			return false;
		}

		String normalizedMessage = normalizeComparable(pendingMessage);
		if (normalizedMessage.isBlank()) {
			return true;
		}

		return isTicketModeSelection(normalizedMessage)
			|| normalizedMessage.equals("abrindo chamado")
			|| isNormalConversationSelection(normalizedMessage)
			|| isSwitchTicketCommand(normalizedMessage)
			|| isRestartCommand(normalizedMessage)
			|| isGreetingMessage(normalizedMessage);
	}

	private User resolveOrCreateRequester(
		String normalizedPhone,
		String whatsappTransportId,
		String fullName,
		String email
	) {
		emailDomainValidationService.ensurePublicEmailDomainExists(email);
		String stableRequesterTransportId = normalizeRequesterTransportId(whatsappTransportId);
		java.util.Optional<User> requesterByEmail = scopedUserLookupService.findUniqueByEmailInCurrentTenant(email)
			.filter(user -> !isResponderSideUser(user));
		java.util.Optional<User> requesterByContact = resolveExistingRequester(normalizedPhone, whatsappTransportId);
		User requester = requesterByEmail
			.orElseGet(() -> requesterByContact.orElseGet(User::new));
		boolean hasDifferentContactOwner = requesterByEmail.isPresent()
			&& requesterByContact.isPresent()
			&& !requesterByEmail.get().getId().equals(requesterByContact.get().getId());

		if (requester.getId() == null) {
			requester.setFullName(fullName);
			requester.setEmail(email);
			requester.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
			requester.setStatus(UserStatus.ACTIVE);
			requester.setEmailVerified(false);
			requester.setSimplified(true);
			requester.getRoles().add(loadDefaultUserRole());
		}

		if (requester.getFullName() == null || requester.getFullName().isBlank() || requester.isSimplified()) {
			requester.setFullName(fullName);
		}
		if ((requester.getEmail() == null || requester.getEmail().isBlank() || isHelpdeskPlaceholderEmail(requester.getEmail()))
			&& !email.isBlank()) {
			requester.setEmail(email);
		}

		if (!hasDifferentContactOwner) {
			if (looksLikeHumanPhoneNumber(normalizedPhone)) {
				requester.setPhoneNumber(normalizedPhone);
			}
			if (!stableRequesterTransportId.isBlank()) {
				requester.setWhatsappTransportId(stableRequesterTransportId);
			} else if (isUnstableRequesterTransportId(requester.getWhatsappTransportId())) {
				requester.setWhatsappTransportId(null);
			}
		} else {
			logger.info(
				"Solicitante por email reutilizado sem sobrescrever contato do WhatsApp: requesterId={}, phone={}, transportId={}",
				requester.getId(),
				normalizedPhone,
				whatsappTransportId
			);
		}

		return userRepository.save(requester);
	}

	private java.util.Optional<User> resolveExistingRequester(String normalizedPhone, String whatsappTransportId) {
		String stableRequesterTransportId = normalizeRequesterTransportId(whatsappTransportId);
		if (!stableRequesterTransportId.isBlank()) {
			java.util.Optional<User> byTransportId = scopedUserLookupService
				.findAllByWhatsappTransportIdInCurrentTenant(stableRequesterTransportId)
				.stream()
				.filter(user -> !isResponderSideUser(user))
				.findFirst();
			if (byTransportId.isPresent()) {
				return byTransportId;
			}
		}

		if (!normalizedPhone.isBlank()) {
			return scopedUserLookupService.findAllByPhoneNumberInCurrentTenant(normalizedPhone).stream()
				.filter(user -> !isResponderSideUser(user))
				.findFirst();
		}

		return java.util.Optional.empty();
	}

	private boolean isResponderSideUser(User user) {
		return user != null && (hasRole(user, "admin") || hasRole(user, "employee"));
	}

	private Role loadDefaultUserRole() {
		return roleRepository.findByCode("USER")
			.orElseThrow(() -> new NotFoundException("Perfil padrão de usuário não encontrado."));
	}

	private boolean hasRole(User user, String roleCode) {
		return user != null
			&& user.getRoles().stream().anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private List<Sector> loadCompanySectors(User companyOwner) {
		List<Sector> sectors = sectorRepository.findActiveByCreatedByIdOrderByNameAsc(companyOwner.getId());
		if (sectors.isEmpty()) {
			throw new IllegalArgumentException("A empresa não possui setores ativos para atendimento no WhatsApp.");
		}
		return sectors;
	}

	private Sector resolveSectorSelection(String body, List<Sector> sectors) {
		String normalizedBody = normalizeComparable(body);
		if (normalizedBody.isBlank()) {
			return null;
		}

		try {
			int position = Integer.parseInt(normalizedBody);
			if (position >= 1 && position <= sectors.size()) {
				return sectors.get(position - 1);
			}
		} catch (NumberFormatException ignored) {
		}

		return sectors.stream()
			.filter(sector -> normalizeComparable(sector.getName()).equals(normalizedBody))
			.findFirst()
			.orElse(null);
	}

	private String buildSectorPrompt(User companyOwner, String prefix) {
		List<Sector> sectors = loadCompanySectors(companyOwner);
		StringBuilder builder = new StringBuilder();

		if (prefix == null || prefix.isBlank()) {
			builder.append("Escolha o setor:");
			builder.append("\nEnvie *reiniciar* para recomeçar ou *cancelar* para sair.");
		} else {
			builder.append(prefix.trim());
		}

		for (int index = 0; index < sectors.size(); index++) {
			builder.append("\n");
			builder.append(index + 1);
			builder.append(". ");
			builder.append(sectors.get(index).getName());
		}

		return builder.toString();
	}

	private String buildAssigneePrompt(User companyOwner, Sector sector, String prefix) {
		List<TicketTargetAssigneeResponse> assignees = ticketService.listAvailableAssigneesForSector(
			sector.getId(),
			companyOwner.getId()
		);
		StringBuilder builder = new StringBuilder();
		builder.append(prefix == null || prefix.isBlank()
			? "Escolha quem deve receber o chamado:"
			: prefix.trim());
		builder.append("\n1. Aleatoriamente");

		for (int index = 0; index < assignees.size(); index++) {
			builder.append("\n");
			builder.append(index + 2);
			builder.append(". ");
			builder.append(assignees.get(index).fullName());
		}

		builder.append("\n\nResponda com o número, nome ou *aleatoriamente*.");
		return builder.toString();
	}

	private AssigneeSelection resolveAssigneeSelection(String body, List<TicketTargetAssigneeResponse> assignees) {
		String normalizedBody = normalizeComparable(body);
		if (normalizedBody.isBlank()) {
			return new AssigneeSelection(null, false);
		}

		if (isAutomaticAssigneeSelection(normalizedBody)) {
			return new AssigneeSelection(null, true);
		}

		try {
			int position = Integer.parseInt(normalizedBody);
			if (position >= 2 && position <= assignees.size() + 1) {
				return new AssigneeSelection(assignees.get(position - 2).id(), true);
			}
		} catch (NumberFormatException ignored) {
		}

		return assignees.stream()
			.filter(assignee ->
				normalizeComparable(assignee.fullName()).equals(normalizedBody)
					|| normalizeComparable(assignee.email()).equals(normalizedBody))
			.findFirst()
			.map(assignee -> new AssigneeSelection(assignee.id(), true))
			.orElseGet(() -> new AssigneeSelection(null, false));
	}

	private boolean isAutomaticAssigneeSelection(String value) {
		String normalizedValue = normalizeComparable(value);
		return normalizedValue.equals("1")
			|| normalizedValue.equals("aleatoriamente")
			|| normalizedValue.equals("aleatorio")
			|| normalizedValue.equals("automaticamente")
			|| normalizedValue.equals("automatico")
			|| normalizedValue.equals("qualquer um");
	}

	private String describeAssigneeSelection(UUID assignedToUserId, List<TicketTargetAssigneeResponse> assignees) {
		if (assignedToUserId == null) {
			return "*aleatoriamente*";
		}

		return assignees.stream()
			.filter(assignee -> assignee.id().equals(assignedToUserId))
			.map(assignee -> "*" + assignee.fullName() + "*")
			.findFirst()
			.orElse("*o funcionário selecionado*");
	}

	private boolean hasActiveOpenTicket(WhatsappConversation conversation) {
		return conversation.getActiveTicket() != null
			&& conversation.getActiveTicket().getStatus() != null
			&& !"CLOSED".equalsIgnoreCase(conversation.getActiveTicket().getStatus().getCode())
			&& conversation.getActiveTicket().getClosedAt() == null;
	}

	private boolean isNewTicketCreationStep(WhatsappConversationStep step) {
		if (step == null) {
			return false;
		}

		return switch (step) {
			case ASK_INITIAL_MODE,
				ASK_INACTIVITY_MESSAGE_DESTINATION,
				NORMAL_CONVERSATION_ACTIVE,
				NORMAL_CONVERSATION_CLOSED,
				ASK_ACTIVE_TICKET_SELECTION,
				ACTIVE_TICKET -> false;
			case ASK_REUSE_REQUESTER_DATA,
				ASK_SECTOR,
				ASK_ASSIGNEE,
				ASK_NAME,
				ASK_EMAIL,
				ASK_DOCUMENT,
				ASK_SUBJECT,
				ASK_DESCRIPTION -> true;
		};
	}

	private boolean canInterruptForTicketSwitch(WhatsappConversationStep step) {
		if (step == null) {
			return false;
		}

		return switch (step) {
			case ASK_INITIAL_MODE,
				ASK_INACTIVITY_MESSAGE_DESTINATION,
				NORMAL_CONVERSATION_ACTIVE,
				NORMAL_CONVERSATION_CLOSED -> false;
			case ASK_REUSE_REQUESTER_DATA,
				ASK_SECTOR,
				ASK_ASSIGNEE,
				ASK_NAME,
				ASK_EMAIL,
				ASK_DOCUMENT,
				ACTIVE_TICKET,
				ASK_ACTIVE_TICKET_SELECTION -> true;
			case ASK_SUBJECT, ASK_DESCRIPTION -> false;
		};
	}

	private void discardInProgressNewTicketData(WhatsappConversation conversation) {
		conversation.setSector(null);
		conversation.setPendingMessage(null);
		conversation.setPendingName(null);
		conversation.setPendingEmail(null);
		conversation.setPendingDocument(null);
		conversation.setPendingAssignedUserId(null);
		conversation.setPendingSubject(null);
	}

	private void clearPendingResumeState(WhatsappConversation conversation) {
		conversation.setPendingResumeMessage(null);
		conversation.setPendingResumeAttachments(null);
	}

	private void resetConversation(WhatsappConversation conversation) {
		prepareForInitialMode(conversation);
	}

	private boolean isRestartCommand(String body) {
		String normalizedBody = normalizeComparable(body);
		return normalizedBody.equals("reiniciar")
			|| normalizedBody.equals("reiniciar atendimento");
	}

	private boolean isOpenNewTicketCommand(String body) {
		String normalizedBody = normalizeComparable(body);
		return normalizedBody.equals("abrir novo chamado")
			|| normalizedBody.equals("novo chamado")
			|| normalizedBody.equals("abrir chamado")
			|| normalizedBody.equals("abrindo chamado");
	}

	private boolean isTicketModeSelection(String body) {
		String normalizedBody = normalizeComparable(body);
		return normalizedBody.equals("1")
			|| normalizedBody.equals("criar chamado")
			|| normalizedBody.equals("criar um chamado")
			|| isOpenNewTicketCommand(body);
	}

	private boolean isNormalConversationSelection(String body) {
		String normalizedBody = normalizeComparable(body);
		return normalizedBody.equals("2")
			|| normalizedBody.equals("conversa normal")
			|| normalizedBody.equals("normal")
			|| normalizedBody.equals("sem chamado")
			|| normalizedBody.equals("conversa sem chamado");
	}

	private boolean isSwitchTicketCommand(String body) {
		String normalizedBody = normalizeComparable(body);
		return normalizedBody.equals("trocar chamado")
			|| normalizedBody.equals("mudar chamado")
			|| normalizedBody.equals("alterar chamado")
			|| normalizedBody.equals("selecionar chamado");
	}

	private boolean isCancelCommand(String body) {
		String normalizedBody = normalizeComparable(body);
		return normalizedBody.equals("cancelar")
			|| normalizedBody.equals("cancelar chamado")
			|| normalizedBody.equals("desistir");
	}

	private boolean isFinishConversationCommand(String body) {
		return normalizeComparable(body).equals("finalizar conversa");
	}

	private boolean isGreetingMessage(String body) {
		String normalizedBody = normalizeComparable(body);
		return normalizedBody.equals("oi")
			|| normalizedBody.equals("ola")
			|| normalizedBody.equals("olá")
			|| normalizedBody.equals("bom dia")
			|| normalizedBody.equals("boa tarde")
			|| normalizedBody.equals("boa noite");
	}

	private void replyWithMessage(User companyOwner, String phoneNumber, String message) {
		logger.info(
			"Enviando resposta automática do WhatsApp: companyOwnerId={}, recipient={}, bodyPreview={}",
			companyOwner.getId(),
			phoneNumber,
			preview(message)
		);
		try {
			recordAutomatedOutboundMessage(companyOwner, phoneNumber, message);
			whatsappService.sendMessage(companyOwner, phoneNumber, message);
		} catch (RuntimeException exception) {
			logger.error("Falha ao enviar resposta automática do WhatsApp para {}", phoneNumber, exception);
		}
	}

	private void recordAutomatedOutboundMessage(User companyOwner, String phoneNumber, String message) {
		automatedOutboundMessageCache.put(
			buildAutomatedOutboundCacheKey(
				whatsappService.buildSessionName(companyOwner),
				phoneNumber,
				message
			),
			OffsetDateTime.now()
		);
	}

	private boolean matchesRecentAutomatedOutbound(String sessionName, String phoneNumber, String message) {
		OffsetDateTime createdAt = automatedOutboundMessageCache.remove(
			buildAutomatedOutboundCacheKey(sessionName, phoneNumber, message)
		);
		return createdAt != null && createdAt.isAfter(OffsetDateTime.now().minusMinutes(AUTOMATED_OUTBOUND_CACHE_MINUTES));
	}

	private String buildAutomatedOutboundCacheKey(String sessionName, String phoneNumber, String message) {
		return normalizeComparable(sessionName)
			+ "|"
			+ normalizeComparable(firstNonBlank(phoneNumber, ""))
			+ "|"
			+ normalizeComparable(firstNonBlank(message, ""));
	}

	private OffsetDateTime maxTimestamp(OffsetDateTime first, OffsetDateTime second) {
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}
		return first.isAfter(second) ? first : second;
	}

	private String serializePendingAttachments(List<TicketService.IncomingAttachment> attachments) {
		if (attachments == null || attachments.isEmpty()) {
			return null;
		}

		try {
			return objectMapper.writeValueAsString(
				attachments.stream()
					.map(attachment -> new PendingAttachmentPayload(
						attachment.originalFileName(),
						attachment.contentType(),
						Base64.getEncoder().encodeToString(attachment.content())
					))
					.toList()
			);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Não foi possível guardar os anexos pendentes da conversa.", exception);
		}
	}

	private List<TicketService.IncomingAttachment> deserializePendingAttachments(String serializedAttachments) {
		if (serializedAttachments == null || serializedAttachments.isBlank()) {
			return List.of();
		}

		try {
			PendingAttachmentPayload[] payloads = objectMapper.readValue(serializedAttachments, PendingAttachmentPayload[].class);
			List<TicketService.IncomingAttachment> attachments = new ArrayList<>();
			for (PendingAttachmentPayload payload : payloads) {
				attachments.add(
					new TicketService.IncomingAttachment(
						payload.originalFileName(),
						payload.contentType(),
						Base64.getDecoder().decode(payload.base64Content())
					)
				);
			}
			return attachments;
		} catch (Exception exception) {
			logger.warn("Anexos pendentes da conversa ignorados por conteúdo inválido.", exception);
			return List.of();
		}
	}

	private java.util.Optional<WhatsappConversation> resolveConversation(
		UUID companyOwnerId,
		String normalizedPhone,
		String whatsappTransportId
	) {
		java.util.Optional<WhatsappConversation> byTransportId = whatsappTransportId.isBlank()
			? java.util.Optional.empty()
			: whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(companyOwnerId, whatsappTransportId);
		java.util.Optional<WhatsappConversation> byPhone = normalizedPhone.isBlank()
			? java.util.Optional.empty()
			: whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwnerId, normalizedPhone);

		if (byTransportId.isEmpty()) {
			return byPhone;
		}
		if (byPhone.isEmpty()) {
			return byTransportId;
		}

		WhatsappConversation transportConversation = byTransportId.get();
		WhatsappConversation phoneConversation = byPhone.get();
		if (transportConversation.getId().equals(phoneConversation.getId())) {
			return byTransportId;
		}

		WhatsappConversation canonicalConversation = chooseCanonicalConversation(phoneConversation, transportConversation);
		WhatsappConversation staleConversation = canonicalConversation.getId().equals(phoneConversation.getId())
			? transportConversation
			: phoneConversation;

		mergeConversationState(canonicalConversation, staleConversation);
		whatsappConversationRepository.delete(staleConversation);
		return java.util.Optional.of(canonicalConversation);
	}

	private WhatsappConversation chooseCanonicalConversation(
		WhatsappConversation phoneConversation,
		WhatsappConversation transportConversation
	) {
		int phoneRank = conversationStateRank(phoneConversation);
		int transportRank = conversationStateRank(transportConversation);
		if (transportRank > phoneRank) {
			return transportConversation;
		}
		if (phoneRank > transportRank) {
			return phoneConversation;
		}

		OffsetDateTime phoneUpdatedAt = phoneConversation.getUpdatedAt();
		OffsetDateTime transportUpdatedAt = transportConversation.getUpdatedAt();
		if (phoneUpdatedAt == null) {
			return transportConversation;
		}
		if (transportUpdatedAt == null) {
			return phoneConversation;
		}
		return transportUpdatedAt.isAfter(phoneUpdatedAt) ? transportConversation : phoneConversation;
	}

	private int conversationStateRank(WhatsappConversation conversation) {
		if (conversation == null) {
			return -1;
		}
		if (conversation.getActiveTicket() != null) {
			return 5;
		}
		if (conversation.getCurrentStep() == WhatsappConversationStep.ACTIVE_TICKET) {
			return 4;
		}
		if (conversation.getCurrentStep() == WhatsappConversationStep.ASK_DESCRIPTION) {
			return 3;
		}
		if (conversation.getCurrentStep() == WhatsappConversationStep.ASK_EMAIL
			|| conversation.getCurrentStep() == WhatsappConversationStep.ASK_NAME
			|| conversation.getCurrentStep() == WhatsappConversationStep.ASK_ASSIGNEE
			|| conversation.getCurrentStep() == WhatsappConversationStep.ASK_SECTOR) {
			return 2;
		}
		if (conversation.getCurrentStep() == WhatsappConversationStep.ASK_INITIAL_MODE) {
			return 1;
		}
		return 0;
	}

	private void mergeConversationState(WhatsappConversation canonicalConversation, WhatsappConversation staleConversation) {
		if (canonicalConversation == null || staleConversation == null) {
			return;
		}
		if (canonicalConversation.getActiveTicket() == null) {
			canonicalConversation.setActiveTicket(staleConversation.getActiveTicket());
		}
		if (canonicalConversation.getSector() == null) {
			canonicalConversation.setSector(staleConversation.getSector());
		}
		if ((canonicalConversation.getPendingName() == null || canonicalConversation.getPendingName().isBlank())
			&& staleConversation.getPendingName() != null
			&& !staleConversation.getPendingName().isBlank()) {
			canonicalConversation.setPendingName(staleConversation.getPendingName());
		}
		if ((canonicalConversation.getPendingEmail() == null || canonicalConversation.getPendingEmail().isBlank())
			&& staleConversation.getPendingEmail() != null
			&& !staleConversation.getPendingEmail().isBlank()) {
			canonicalConversation.setPendingEmail(staleConversation.getPendingEmail());
		}
		if ((canonicalConversation.getPendingMessage() == null || canonicalConversation.getPendingMessage().isBlank())
			&& staleConversation.getPendingMessage() != null
			&& !staleConversation.getPendingMessage().isBlank()) {
			canonicalConversation.setPendingMessage(staleConversation.getPendingMessage());
		}
		if (canonicalConversation.getPendingAssignedUserId() == null) {
			canonicalConversation.setPendingAssignedUserId(staleConversation.getPendingAssignedUserId());
		}
		if (canonicalConversation.getCurrentStep() == null
			|| conversationStateRank(staleConversation) > conversationStateRank(canonicalConversation)) {
			canonicalConversation.setCurrentStep(staleConversation.getCurrentStep());
		}
		if (!canonicalConversation.isNormalConversationActive() && staleConversation.isNormalConversationActive()) {
			canonicalConversation.setNormalConversationActive(true);
		}
	}

	private String resolveSessionName(JsonNode payload) {
		return firstNonBlank(
			extractText(payload, "session"),
			extractText(payload, "sessionName"),
			extractNestedText(payload, "session", "name"),
			extractNestedText(payload, "payload", "session"),
			extractNestedText(payload, "payload", "sessionName"),
			extractNestedText(payload, "data", "session"),
			extractNestedText(payload, "data", "sessionName"),
			extractPathText(payload, "payload.session.name"),
			extractPathText(payload, "data.session.name"),
			extractDeepText(payload, "session"),
			extractDeepText(payload, "sessionName")
		);
	}

	private String resolveIncomingTransportId(JsonNode payload, boolean fromMe) {
		if (fromMe) {
			return firstWhatsappAddress(
				extractPathText(payload, "key.remoteJid"),
				extractPathText(payload, "message.key.remoteJid"),
				extractPathText(payload, "chat.id"),
				extractPathText(payload, "chatId._serialized"),
				extractPathText(payload, "id.remote"),
				extractPathText(payload, "payload.key.remoteJid"),
				extractPathText(payload, "payload.message.key.remoteJid"),
				extractPathText(payload, "payload.chat.id"),
				extractPathText(payload, "payload.chatId._serialized"),
				extractPathText(payload, "payload.id.remote"),
				extractPathText(payload, "data.key.remoteJid"),
				extractPathText(payload, "data.message.key.remoteJid"),
				extractPathText(payload, "data.chat.id"),
				extractPathText(payload, "data.chatId._serialized"),
				extractPathText(payload, "data.id.remote"),
				extractText(payload, "chatId"),
				extractText(payload, "from"),
				extractNestedText(payload, "payload", "chatId"),
				extractNestedText(payload, "payload", "from"),
				extractNestedText(payload, "data", "chatId"),
				extractNestedText(payload, "data", "from"),
				extractDeepText(payload, "remoteJid"),
				extractDeepText(payload, "chat_id"),
				extractDeepText(payload, "from"),
				extractPathText(payload, "sender.id"),
				extractPathText(payload, "payload.sender.id"),
				extractPathText(payload, "data.sender.id"),
				extractText(payload, "sender_id"),
				extractNestedText(payload, "payload", "sender_id"),
				extractNestedText(payload, "data", "sender_id"),
				extractDeepText(payload, "sender_id")
			);
		}

		return firstWhatsappAddress(
			extractPathText(payload, "key.remoteJid"),
			extractPathText(payload, "message.key.remoteJid"),
			extractPathText(payload, "sender.id"),
			extractPathText(payload, "chat.id"),
			extractPathText(payload, "chatId._serialized"),
			extractPathText(payload, "id.remote"),
			extractPathText(payload, "payload.key.remoteJid"),
			extractPathText(payload, "payload.message.key.remoteJid"),
			extractPathText(payload, "payload.sender.id"),
			extractPathText(payload, "payload.chat.id"),
			extractPathText(payload, "payload.chatId._serialized"),
			extractPathText(payload, "payload.id.remote"),
			extractPathText(payload, "data.key.remoteJid"),
			extractPathText(payload, "data.message.key.remoteJid"),
			extractPathText(payload, "data.sender.id"),
			extractPathText(payload, "data.chat.id"),
			extractPathText(payload, "data.chatId._serialized"),
			extractPathText(payload, "data.id.remote"),
			extractText(payload, "chatId"),
			extractText(payload, "sender_id"),
			extractText(payload, "from"),
			extractNestedText(payload, "payload", "chatId"),
			extractNestedText(payload, "payload", "sender_id"),
			extractNestedText(payload, "payload", "from"),
			extractNestedText(payload, "data", "chatId"),
			extractNestedText(payload, "data", "sender_id"),
			extractNestedText(payload, "data", "from"),
			extractDeepText(payload, "remoteJid"),
			extractDeepText(payload, "chat_id"),
			extractDeepText(payload, "sender_id"),
			extractDeepText(payload, "from")
		);
	}

	private String resolveIncomingPhone(JsonNode payload, String transportId, boolean fromMe) {
		if (fromMe) {
			String rawPhone = firstNonBlank(
				extractPathText(payload, "key.remoteJid"),
				extractPathText(payload, "message.key.remoteJid"),
				extractText(payload, "chatId"),
				extractPathText(payload, "chat.id"),
				extractPathText(payload, "chatId._serialized"),
				extractPathText(payload, "id.remote"),
				extractPathText(payload, "payload.key.remoteJid"),
				extractPathText(payload, "payload.message.key.remoteJid"),
				extractNestedText(payload, "payload", "chatId"),
				extractNestedText(payload, "payload", "from"),
				extractPathText(payload, "data.key.remoteJid"),
				extractPathText(payload, "data.message.key.remoteJid"),
				extractNestedText(payload, "data", "chatId"),
				extractNestedText(payload, "data", "from"),
				extractText(payload, "phone"),
				extractNestedText(payload, "payload", "phone"),
				extractNestedText(payload, "data", "phone"),
				extractDeepText(payload, "remoteJid"),
				extractDeepText(payload, "phone"),
				transportId
			);
			return normalizeWhatsappAddress(rawPhone);
		}

		String rawPhone = firstNonBlank(
			extractText(payload, "phone"),
			extractNestedText(payload, "payload", "phone"),
			extractNestedText(payload, "data", "phone"),
			extractDeepText(payload, "phone"),
			transportId
		);
		return normalizeWhatsappAddress(rawPhone);
	}

	private String resolveIncomingBody(JsonNode payload) {
		return firstNonBlank(
			extractText(payload, "body"),
			extractText(payload, "text"),
			extractText(payload, "content"),
			extractPathText(payload, "message.conversation"),
			extractPathText(payload, "message.extendedTextMessage.text"),
			extractPathText(payload, "message.imageMessage.caption"),
			extractPathText(payload, "message.videoMessage.caption"),
			extractNestedText(payload, "message", "text"),
			extractNestedText(payload, "message", "body"),
			extractNestedText(payload, "payload", "body"),
			extractNestedText(payload, "payload", "text"),
			extractNestedText(payload, "data", "body"),
			extractNestedText(payload, "data", "text"),
			extractPathText(payload, "message.content"),
			extractPathText(payload, "payload.message.conversation"),
			extractPathText(payload, "payload.message.extendedTextMessage.text"),
			extractPathText(payload, "payload.message.imageMessage.caption"),
			extractPathText(payload, "payload.message.videoMessage.caption"),
			extractPathText(payload, "payload.message.text"),
			extractPathText(payload, "payload.message.body"),
			extractPathText(payload, "data.message.conversation"),
			extractPathText(payload, "data.message.extendedTextMessage.text"),
			extractPathText(payload, "data.message.imageMessage.caption"),
			extractPathText(payload, "data.message.videoMessage.caption"),
			extractPathText(payload, "data.message.text"),
			extractPathText(payload, "data.message.body"),
			extractDeepText(payload, "body"),
			extractDeepText(payload, "text"),
			extractDeepText(payload, "content"),
			extractDeepText(payload, "conversation"),
			extractDeepText(payload, "caption")
		);
	}

	private List<TicketService.IncomingAttachment> resolveIncomingAttachments(JsonNode payload) {
		JsonNode attachmentsNode = firstArrayNode(
			extractPathNode(payload, "attachments"),
			extractPathNode(payload, "payload.attachments"),
			extractPathNode(payload, "data.attachments"),
			extractPathNode(payload, "message.attachments")
		);

		if (attachmentsNode == null || !attachmentsNode.isArray()) {
			return List.of();
		}

		List<TicketService.IncomingAttachment> attachments = new ArrayList<>();
		for (JsonNode attachmentNode : attachmentsNode) {
			String base64Content = firstNonBlank(
				extractText(attachmentNode, "base64"),
				extractText(attachmentNode, "data"),
				extractText(attachmentNode, "content")
			);
			if (base64Content.isBlank()) {
				continue;
			}

			try {
				byte[] content = Base64.getDecoder().decode(base64Content);
				if (content.length == 0) {
					continue;
				}

				attachments.add(new TicketService.IncomingAttachment(
					firstNonBlank(
						extractText(attachmentNode, "originalFileName"),
						extractText(attachmentNode, "fileName"),
						extractText(attachmentNode, "filename")
					),
					firstNonBlank(
						extractText(attachmentNode, "contentType"),
						extractText(attachmentNode, "mimeType"),
						extractText(attachmentNode, "mimetype")
					),
					content
				));
			} catch (IllegalArgumentException exception) {
				logger.warn("Anexo do WhatsApp ignorado por base64 inválido.");
			}
		}

		return attachments;
	}

	private boolean resolveFromMe(JsonNode payload) {
		return extractBoolean(payload, "fromMe")
			|| extractBoolean(payload, "isFromMe")
			|| extractNestedBoolean(payload, "payload", "fromMe")
			|| extractNestedBoolean(payload, "payload", "isFromMe")
			|| extractNestedBoolean(payload, "data", "fromMe")
			|| extractNestedBoolean(payload, "data", "isFromMe")
			|| extractDeepBoolean(payload, "fromMe")
			|| extractDeepBoolean(payload, "isFromMe");
	}

	private boolean isGroupMessage(JsonNode payload) {
		String groupMarker = firstNonBlank(
			extractText(payload, "chatId"),
			extractText(payload, "from"),
			extractPathText(payload, "chat.id"),
			extractPathText(payload, "chatId._serialized"),
			extractPathText(payload, "id.remote"),
			extractNestedText(payload, "payload", "chatId"),
			extractNestedText(payload, "payload", "from"),
			extractNestedText(payload, "data", "chatId"),
			extractNestedText(payload, "data", "from")
		);
		return groupMarker.contains("@g.us")
			|| extractBoolean(payload, "isGroup")
			|| extractNestedBoolean(payload, "payload", "isGroup")
			|| extractNestedBoolean(payload, "data", "isGroup")
			|| extractDeepBoolean(payload, "isGroup");
	}

	private JsonNode parsePayload(String payload) {
		if (payload == null || payload.isBlank()) {
			return null;
		}

		try {
			return objectMapper.readTree(payload);
		} catch (IOException exception) {
			logger.warn("Não foi possível interpretar o payload do webhook do WhatsApp.");
			return null;
		}
	}

	private String extractText(JsonNode payload, String fieldName) {
		if (payload == null || !payload.hasNonNull(fieldName)) {
			return "";
		}
		return payload.get(fieldName).asText("");
	}

	private String extractNestedText(JsonNode payload, String fieldName, String nestedFieldName) {
		if (payload == null || !payload.hasNonNull(fieldName) || !payload.get(fieldName).hasNonNull(nestedFieldName)) {
			return "";
		}
		return payload.get(fieldName).get(nestedFieldName).asText("");
	}

	private String extractPathText(JsonNode payload, String path) {
		if (payload == null || path == null || path.isBlank()) {
			return "";
		}

		JsonNode currentNode = payload;
		for (String pathSegment : path.split("\\.")) {
			if (currentNode == null || !currentNode.hasNonNull(pathSegment)) {
				return "";
			}
			currentNode = currentNode.get(pathSegment);
		}

		return currentNode == null ? "" : currentNode.asText("");
	}

	private JsonNode extractPathNode(JsonNode payload, String path) {
		if (payload == null || path == null || path.isBlank()) {
			return null;
		}

		JsonNode currentNode = payload;
		for (String pathSegment : path.split("\\.")) {
			if (currentNode == null || !currentNode.has(pathSegment)) {
				return null;
			}
			currentNode = currentNode.get(pathSegment);
		}

		return currentNode;
	}

	private boolean extractBoolean(JsonNode payload, String fieldName) {
		return payload != null && payload.hasNonNull(fieldName) && payload.get(fieldName).asBoolean(false);
	}

	private boolean extractNestedBoolean(JsonNode payload, String fieldName, String nestedFieldName) {
		return payload != null
			&& payload.hasNonNull(fieldName)
			&& payload.get(fieldName).hasNonNull(nestedFieldName)
			&& payload.get(fieldName).get(nestedFieldName).asBoolean(false);
	}

	private JsonNode firstArrayNode(JsonNode... nodes) {
		if (nodes == null) {
			return null;
		}

		for (JsonNode node : nodes) {
			if (node != null && node.isArray()) {
				return node;
			}
		}

		return null;
	}

	private String extractDeepText(JsonNode payload, String fieldName) {
		if (payload == null || fieldName == null || fieldName.isBlank()) {
			return "";
		}

		if (payload.hasNonNull(fieldName)) {
			return payload.get(fieldName).asText("");
		}

		for (JsonNode child : payload) {
			String nestedResult = extractDeepText(child, fieldName);
			if (!nestedResult.isBlank()) {
				return nestedResult;
			}
		}

		return "";
	}

	private boolean extractDeepBoolean(JsonNode payload, String fieldName) {
		if (payload == null || fieldName == null || fieldName.isBlank()) {
			return false;
		}

		if (payload.hasNonNull(fieldName)) {
			return payload.get(fieldName).asBoolean(false);
		}

		for (JsonNode child : payload) {
			if (extractDeepBoolean(child, fieldName)) {
				return true;
			}
		}

		return false;
	}

	private String firstWhatsappAddress(String... values) {
		for (String value : values) {
			String normalized = normalizeWhatsappAddress(value);
			if (!normalized.isBlank()) {
				return normalized;
			}
		}
		return "";
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return "";
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private String normalizeInboundMessage(String body) {
		String normalizedBody = body == null ? "" : body.trim();
		if (normalizedBody.length() > 5000) {
			return normalizedBody.substring(0, 5000);
		}
		return normalizedBody;
	}

	private String normalizePhone(String phone) {
		String normalizedAddress = normalizeWhatsappAddress(phone);
		return normalizedAddress.replaceAll("\\D+", "");
	}

	private boolean looksLikeHumanPhoneNumber(String phone) {
		if (phone == null || phone.isBlank()) {
			return false;
		}

		String digits = phone.replaceAll("\\D+", "");
		if (digits.startsWith("55") && digits.length() == 13) {
			digits = digits.substring(2);
		}

		return digits.length() == 10 || digits.length() == 11;
	}

	private String normalizeWhatsappAddress(String phone) {
		if (phone == null) {
			return "";
		}

		String normalized = phone.trim();
		if (normalized.isBlank()) {
			return "";
		}

		int atIndex = normalized.indexOf('@');
		if (atIndex >= 0) {
			String localPart = normalized.substring(0, atIndex);
			int deviceSeparatorIndex = localPart.indexOf(':');
			if (deviceSeparatorIndex >= 0) {
				localPart = localPart.substring(0, deviceSeparatorIndex);
			}
			normalized = localPart + normalized.substring(atIndex);
		} else {
			int deviceSeparatorIndex = normalized.indexOf(':');
			if (deviceSeparatorIndex >= 0) {
				normalized = normalized.substring(0, deviceSeparatorIndex);
			}
		}

		return normalized;
	}

	private String normalizeWhatsappTransportId(String phone) {
		String normalized = normalizeWhatsappAddress(phone);
		return normalized.contains("@") ? normalized : "";
	}

	private String normalizeRequesterTransportId(String whatsappTransportId) {
		String normalized = normalizeWhatsappTransportId(whatsappTransportId);
		return isUnstableRequesterTransportId(normalized) ? "" : normalized;
	}

	private boolean isUnstableRequesterTransportId(String whatsappTransportId) {
		if (whatsappTransportId == null || whatsappTransportId.isBlank()) {
			return false;
		}
		return normalizeWhatsappTransportId(whatsappTransportId).endsWith("@lid");
	}

	private String resolveReplyTarget(WhatsappConversation conversation, String normalizedPhone) {
		if (conversation.getWhatsappTransportId() != null && !conversation.getWhatsappTransportId().isBlank()) {
			return conversation.getWhatsappTransportId();
		}
		if (normalizedPhone != null && !normalizedPhone.isBlank()) {
			return normalizedPhone;
		}
		return normalizedPhone;
	}

	private String normalizeComparable(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return normalized.replaceAll("\\s+", " ");
	}

	private String normalizePersonName(String value) {
		String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
		return normalized.length() <= 150 ? normalized : normalized.substring(0, 150);
	}

	private boolean isValidPersonName(String value) {
		String normalized = normalizePersonName(value);
		if (normalized.length() < 3 || !normalized.contains(" ")) {
			return false;
		}
		return normalized.chars().filter(Character::isLetter).count() >= 3;
	}

	private String normalizeContactEmail(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (normalized.length() > 150) {
			return "";
		}
		if (!normalized.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
			return "";
		}
		return normalized;
	}

	private boolean isHelpdeskPlaceholderEmail(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		return normalized.endsWith("@helpdesk.local");
	}

	private String preview(String value) {
		String normalized = value == null ? "" : value.trim();
		return normalized.length() <= 100 ? normalized : normalized.substring(0, 100) + "...";
	}

	private String previewRawPayload(String value) {
		String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
		return normalized.length() <= 1200 ? normalized : normalized.substring(0, 1200) + "...";
	}

	private boolean isMessageEvent(String event) {
		String normalizedEvent = normalizeComparable(event);
		return normalizedEvent.contains("message")
			&& !normalizedEvent.contains("ack")
			&& !normalizedEvent.contains("reaction")
			&& !normalizedEvent.contains("revoked")
			&& !normalizedEvent.contains("poll");
	}

	private record AssigneeSelection(UUID assignedToUserId, boolean valid) {
	}

	private record PendingAttachmentPayload(String originalFileName, String contentType, String base64Content) {
	}
}
