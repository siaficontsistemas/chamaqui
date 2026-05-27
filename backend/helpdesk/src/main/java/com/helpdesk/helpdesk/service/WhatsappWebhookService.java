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
		String transportId = resolveIncomingTransportId(payloadJson);
		String phone = resolveIncomingPhone(payloadJson, transportId);
		String body = resolveIncomingBody(payloadJson);
		List<TicketService.IncomingAttachment> attachments = resolveIncomingAttachments(payloadJson);
		boolean fromMe = resolveFromMe(payloadJson);
		boolean groupMessage = isGroupMessage(payloadJson);

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
				createdConversation.setCurrentStep(WhatsappConversationStep.ASK_SECTOR);
				return createdConversation;
			});

		conversation.setCompanyOwner(companyOwner);
		conversation.setPhoneNumber(normalizedPhone);
		conversation.setWhatsappTransportId(whatsappTransportId);
		conversation.setLastInboundMessageAt(OffsetDateTime.now());
		if (conversation.getCurrentStep() == null) {
			conversation.setCurrentStep(WhatsappConversationStep.ASK_SECTOR);
		}

		if (isCancelCommand(normalizedBody) && isNewTicketCreationStep(conversation.getCurrentStep())) {
			cancelNewTicketFlow(companyOwner, conversation, resolveReplyTarget(conversation, normalizedPhone));
			return;
		}

		String replyTarget = resolveReplyTarget(conversation, normalizedPhone);
		boolean isNewConversation = conversation.getId() == null;

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

		if (isNewConversation || isGreetingMessage(normalizedBody)) {
			startNewTicketFlow(companyOwner, conversation, replyTarget, null);
			return;
		}

		switch (conversation.getCurrentStep()) {
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

	private boolean handleOpenTicketRouting(
		User companyOwner,
		WhatsappConversation conversation,
		String replyTarget,
		String body,
		List<TicketService.IncomingAttachment> attachments
	) {
		List<Ticket> openTickets = loadOpenTicketsForConversation(companyOwner, conversation);
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
						"""
						Você possui 1 chamado em aberto. Vou direcionar suas próximas mensagens para o chamado *%s*.
						Se quiser abrir outro depois, envie *abrir novo chamado*.
						""".formatted(selectedTicket.getProtocol()).trim()
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
					"Seu atendimento já está em andamento e não pode ser reiniciado agora. Aguarde o encerramento pelo funcionário."
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
			"""
			Perfeito. Vou direcionar suas próximas mensagens para o chamado *%s*.
			Se quiser trocar depois, envie *trocar chamado*.
			""".formatted(selectedTicket.getProtocol()).trim()
		);
		return true;
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

	private List<Ticket> loadOpenTicketsForConversation(User companyOwner, WhatsappConversation conversation) {
		User requesterByPhone = resolveExistingRequester(conversation.getPhoneNumber(), conversation.getWhatsappTransportId())
			.orElse(null);
		String pendingEmail = normalizeContactEmail(conversation.getPendingEmail());
		User requesterByEmail = pendingEmail.isBlank()
			? null
			: scopedUserLookupService.findUniqueByEmailInCurrentTenant(pendingEmail).orElse(null);
		User requesterFromActiveTicket = conversation.getActiveTicket() == null
			? null
			: conversation.getActiveTicket().getRequester();

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
		resetConversation(conversation);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			"""
			Tudo bem. Cancelei a abertura deste chamado e apaguei os dados informados até agora.
			Se quiser abrir outro depois, envie *abrir novo chamado*.
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

	private String buildReuseRequesterPrompt(String prefix, WhatsappConversation conversation) {
		StringBuilder builder = new StringBuilder();
		if (prefix != null && !prefix.isBlank()) {
			builder.append(prefix.trim());
			builder.append("\n");
		}
		builder.append("Esses são os dados do último chamado que você fez:");
		builder.append("\nNome: ").append(conversation.getPendingName());
		builder.append("\nEmail: ").append(conversation.getPendingEmail());
		builder.append("\n\nQuer prosseguir com o próximo chamado usando esses mesmos dados?");
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
			conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
			whatsappConversationRepository.save(conversation);
			replyWithMessage(
				companyOwner,
				replyTarget,
				"""
				Perfeito. O chamado vai para %s.
				Vamos usar os mesmos dados do ultimo atendimento:
				Nome: *%s*
				Email: *%s*

				Agora envie a *primeira mensagem* do seu chamado.
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
			Perfeito. O chamado vai para %s.
			Para continuar, me informe seu *nome completo*.
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
		conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
		whatsappConversationRepository.save(conversation);
		replyWithMessage(
			companyOwner,
			replyTarget,
			"Perfeito. Agora envie a *primeira mensagem* do seu chamado. Se desistir, envie *cancelar*."
		);
	}

	private void handleDocumentStep(User companyOwner, WhatsappConversation conversation, String replyTarget) {
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
		if (normalizedDescription.length() < 10 && attachments != null && !attachments.isEmpty()) {
			normalizedDescription = "Arquivo enviado pelo WhatsApp.";
		}
		if (normalizedDescription.length() < 10) {
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
		try {
			User requester = resolveOrCreateRequester(
				conversation.getPhoneNumber(),
				conversation.getWhatsappTransportId(),
				pendingName,
				pendingEmail
			)
			;
			Ticket createdTicket = ticketService.createFromWhatsapp(
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
			conversation.setActiveTicket(createdTicket);
			conversation.setPendingName(requester.getFullName());
			conversation.setPendingEmail(requester.getEmail());
			conversation.setPendingDocument(requester.getDocumentNumber());
			conversation.setPendingMessage(null);
			conversation.setCurrentStep(WhatsappConversationStep.ACTIVE_TICKET);
			whatsappConversationRepository.saveAndFlush(conversation);
			List<Ticket> openTickets = loadOpenTicketsForConversation(companyOwner, conversation);
			String multipleOpenTicketsGuidance = openTickets.size() >= 2
				? """

				Como você possui mais de um chamado em aberto, as próximas mensagens que você enviar serão para o último chamado que você criou.
				Se quiser trocar de chamado, envie *trocar chamado*.
				""".trim()
				: "";

			replyWithMessage(
				companyOwner,
				replyTarget,
				"""
				Chamado aberto com sucesso.
				Protocolo: %s
				Setor: %s
				Destinatário: %s

				Pode continuar enviando mensagens por aqui que elas serão adicionadas ao chamado.
				Se quiser abrir mais um chamado, digite *abrir novo chamado*.
				%s
				""".formatted(
					createdTicket.getProtocol(),
					conversation.getSector().getName(),
					createdTicket.getAssignedTo() == null ? "Não informado" : createdTicket.getAssignedTo().getFullName(),
					multipleOpenTicketsGuidance.isBlank() ? "" : "\n" + multipleOpenTicketsGuidance
				).trim()
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
			conversation.setCurrentStep(WhatsappConversationStep.ASK_DESCRIPTION);
			whatsappConversationRepository.save(conversation);
		}
	}

	private User resolveOrCreateRequester(
		String normalizedPhone,
		String whatsappTransportId,
		String fullName,
		String email
	) {
		emailDomainValidationService.ensurePublicEmailDomainExists(email);
		java.util.Optional<User> requesterByEmail = scopedUserLookupService.findUniqueByEmailInCurrentTenant(email);
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
			if (!whatsappTransportId.isBlank()) {
				requester.setWhatsappTransportId(whatsappTransportId);
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
		if (!whatsappTransportId.isBlank()) {
			java.util.Optional<User> byTransportId = userRepository.findByWhatsappTransportId(whatsappTransportId);
			if (byTransportId.isPresent()) {
				return byTransportId;
			}
		}

		if (!normalizedPhone.isBlank()) {
			return userRepository.findByPhoneNumber(normalizedPhone);
		}

		return java.util.Optional.empty();
	}

	private Role loadDefaultUserRole() {
		return roleRepository.findByCode("USER")
			.orElseThrow(() -> new NotFoundException("Perfil padrão de usuário não encontrado."));
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
			builder.append("Olá. Para abrir seu chamado, escolha o setor desejado:");
			builder.append("\nDepois vou pedir nome, email e sua primeira mensagem.");
			builder.append("\nSe precisar corrigir algum dado informado durante essa etapa, envie *reiniciar*.");
			builder.append("\nSe desistir de abrir o chamado, envie *cancelar*.");
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

		builder.append("\n\nResponda com o número, nome do funcionário ou *aleatoriamente*.");
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
			case ASK_REUSE_REQUESTER_DATA,
				ASK_SECTOR,
				ASK_ASSIGNEE,
				ASK_NAME,
				ASK_EMAIL,
				ASK_DOCUMENT,
				ASK_SUBJECT,
				ASK_DESCRIPTION -> true;
			case ASK_ACTIVE_TICKET_SELECTION, ACTIVE_TICKET -> false;
		};
	}

	private boolean canInterruptForTicketSwitch(WhatsappConversationStep step) {
		if (step == null) {
			return false;
		}

		return switch (step) {
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

	private void resetConversation(WhatsappConversation conversation) {
		conversation.setCurrentStep(WhatsappConversationStep.ASK_SECTOR);
		conversation.setSector(null);
		conversation.setPendingMessage(null);
		conversation.setPendingName(null);
		conversation.setPendingEmail(null);
		conversation.setPendingDocument(null);
		conversation.setPendingAssignedUserId(null);
		conversation.setPendingSubject(null);
		conversation.setActiveTicket(null);
		conversation.setLastTicketSelectionPromptAt(null);
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
			|| normalizedBody.equals("abrir chamado");
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
			whatsappService.sendMessage(companyOwner, phoneNumber, message);
		} catch (RuntimeException exception) {
			logger.error("Falha ao enviar resposta automática do WhatsApp para {}", phoneNumber, exception);
		}
	}

	private java.util.Optional<WhatsappConversation> resolveConversation(
		UUID companyOwnerId,
		String normalizedPhone,
		String whatsappTransportId
	) {
		if (!whatsappTransportId.isBlank()) {
			java.util.Optional<WhatsappConversation> byTransportId =
				whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(companyOwnerId, whatsappTransportId);
			if (byTransportId.isPresent()) {
				return byTransportId;
			}
		}

		if (!normalizedPhone.isBlank()) {
			return whatsappConversationRepository.findByCompanyOwnerIdAndPhoneNumber(companyOwnerId, normalizedPhone);
		}

		return java.util.Optional.empty();
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

	private String resolveIncomingTransportId(JsonNode payload) {
		return firstWhatsappAddress(
			extractPathText(payload, "sender.id"),
			extractPathText(payload, "chat.id"),
			extractPathText(payload, "chatId._serialized"),
			extractPathText(payload, "id.remote"),
			extractPathText(payload, "payload.sender.id"),
			extractPathText(payload, "payload.chat.id"),
			extractPathText(payload, "payload.chatId._serialized"),
			extractPathText(payload, "payload.id.remote"),
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
			extractDeepText(payload, "chat_id"),
			extractDeepText(payload, "sender_id"),
			extractDeepText(payload, "from")
		);
	}

	private String resolveIncomingPhone(JsonNode payload, String transportId) {
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
			extractNestedText(payload, "message", "text"),
			extractNestedText(payload, "message", "body"),
			extractNestedText(payload, "payload", "body"),
			extractNestedText(payload, "payload", "text"),
			extractNestedText(payload, "data", "body"),
			extractNestedText(payload, "data", "text"),
			extractPathText(payload, "message.content"),
			extractPathText(payload, "payload.message.text"),
			extractPathText(payload, "payload.message.body"),
			extractPathText(payload, "data.message.text"),
			extractPathText(payload, "data.message.body"),
			extractDeepText(payload, "body"),
			extractDeepText(payload, "text"),
			extractDeepText(payload, "content")
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
		return phone == null ? "" : phone.replaceAll("\\D+", "");
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
		return phone == null ? "" : phone.trim();
	}

	private String normalizeWhatsappTransportId(String phone) {
		String normalized = normalizeWhatsappAddress(phone);
		return normalized.contains("@") ? normalized : "";
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
}
