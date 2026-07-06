package com.helpdesk.helpdesk.service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.SectorMember;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketAssignmentNotification;
import com.helpdesk.helpdesk.domain.TicketAttachment;
import com.helpdesk.helpdesk.domain.TicketChannel;
import com.helpdesk.helpdesk.domain.TicketClosureNotification;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.TicketPriority;
import com.helpdesk.helpdesk.domain.TicketReplyNotification;
import com.helpdesk.helpdesk.domain.TicketStatus;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.ticket.CloseTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketMessageRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.DeleteTicketsRequest;
import com.helpdesk.helpdesk.dto.ticket.RequestTicketTransferRequest;
import com.helpdesk.helpdesk.dto.ticket.TicketAttachmentResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketMessageResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketSummaryResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketTargetAssigneeResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketTransferCandidateResponse;
import com.helpdesk.helpdesk.dto.ticket.UpdateTicketTitleRequest;
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

@Service
public class TicketService {

	private static final int AUTO_TICKET_TITLE_PREVIEW_LENGTH = 30;

	private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;
	private final CompanyPartnershipRepository companyPartnershipRepository;
	private final SectorMemberRepository sectorMemberRepository;
	private final SectorRepository sectorRepository;
	private final TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository;
	private final TicketStatusRepository ticketStatusRepository;
	private final TicketPriorityRepository ticketPriorityRepository;
	private final TicketMessageRepository ticketMessageRepository;
	private final TicketAttachmentRepository ticketAttachmentRepository;
	private final TicketTransferNotificationRepository ticketTransferNotificationRepository;
	private final TicketClosureNotificationRepository ticketClosureNotificationRepository;
	private final TicketReplyNotificationRepository ticketReplyNotificationRepository;
	private final TicketAttachmentStorageService ticketAttachmentStorageService;
	private final TicketClosureEmailService ticketClosureEmailService;
	private final WhatsappService whatsappService;
	private final WhatsappConversationRepository whatsappConversationRepository;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;
	private final AuditTrailService auditTrailService;

	public TicketService(
		TicketRepository ticketRepository,
		UserRepository userRepository,
		CompanyPartnershipRepository companyPartnershipRepository,
		SectorMemberRepository sectorMemberRepository,
		SectorRepository sectorRepository,
		TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository,
		TicketStatusRepository ticketStatusRepository,
		TicketPriorityRepository ticketPriorityRepository,
		TicketMessageRepository ticketMessageRepository,
		TicketAttachmentRepository ticketAttachmentRepository,
		TicketTransferNotificationRepository ticketTransferNotificationRepository,
		TicketClosureNotificationRepository ticketClosureNotificationRepository,
		TicketReplyNotificationRepository ticketReplyNotificationRepository,
		TicketAttachmentStorageService ticketAttachmentStorageService,
		TicketClosureEmailService ticketClosureEmailService,
		WhatsappService whatsappService,
		WhatsappConversationRepository whatsappConversationRepository,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService,
		AuditTrailService auditTrailService
	) {
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
		this.companyPartnershipRepository = companyPartnershipRepository;
		this.sectorMemberRepository = sectorMemberRepository;
		this.sectorRepository = sectorRepository;
		this.ticketAssignmentNotificationRepository = ticketAssignmentNotificationRepository;
		this.ticketStatusRepository = ticketStatusRepository;
		this.ticketPriorityRepository = ticketPriorityRepository;
		this.ticketMessageRepository = ticketMessageRepository;
		this.ticketAttachmentRepository = ticketAttachmentRepository;
		this.ticketTransferNotificationRepository = ticketTransferNotificationRepository;
		this.ticketClosureNotificationRepository = ticketClosureNotificationRepository;
		this.ticketReplyNotificationRepository = ticketReplyNotificationRepository;
		this.ticketAttachmentStorageService = ticketAttachmentStorageService;
		this.ticketClosureEmailService = ticketClosureEmailService;
		this.whatsappService = whatsappService;
		this.whatsappConversationRepository = whatsappConversationRepository;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
		this.auditTrailService = auditTrailService;
	}

	@Transactional(readOnly = true)
	public List<TicketResponse> list(String email, String status) {
		User viewer = loadCurrentUserByEmail(email, "Usuário responsável pela consulta não encontrado.");
		List<String> statusCodes = normalizeStatusCodes(status);
		List<Ticket> tickets = loadVisibleTickets(viewer, statusCodes);

		return tickets.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public TicketResponse get(UUID ticketId, String email) {
		User viewer = loadCurrentUserByEmail(email, "Usuário responsável pela consulta não encontrado.");
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, viewer);
		auditTrailService.recordUserAction("TICKET_VIEWED", viewer, "ticket", ticket.getId());
		return toResponse(ticket);
	}

	@Transactional(readOnly = true)
	public TicketSummaryResponse summary(String email) {
		User viewer = loadCurrentUserByEmail(email, "Usuário responsável pelo resumo não encontrado.");
		List<Ticket> tickets = loadVisibleTickets(viewer, List.of());
		long open = 0;
		long inProgress = 0;
		long closed = 0;

		for (Ticket ticket : tickets) {
			String displayStatusCode = resolveDisplayStatus(ticket).code();

			if ("CLOSED".equals(displayStatusCode)) {
				closed++;
				continue;
			}

			if ("OPEN".equals(displayStatusCode)) {
				open++;
				continue;
			}

			inProgress++;
		}

		return new TicketSummaryResponse(tickets.size(), open, inProgress, closed);
	}

	@Transactional(readOnly = true)
	public List<TicketTransferCandidateResponse> listTransferCandidates(UUID ticketId, String email) {
		User author = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela transferência não encontrado."));
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, author.getEmail());

		return loadTransferCandidates(ticket, author).stream()
			.map(candidate -> new TicketTransferCandidateResponse(
				candidate.getId(),
				candidate.getFullName(),
				candidate.getEmail()
			))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TicketTargetAssigneeResponse> listAvailableAssigneesForSector(UUID sectorId, UUID companyOwnerId) {
		com.helpdesk.helpdesk.domain.Sector sector = sectorRepository.findById(sectorId)
			.orElseThrow(() -> new NotFoundException("Setor não encontrado."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			sector.getCreatedBy().getId(),
			"O setor informado não pertence ao tenant atual."
		);

		UUID effectiveCompanyOwnerId = tenantAccessService.getCurrentTenantOwnerUserId().orElse(companyOwnerId);
		if (effectiveCompanyOwnerId != null && !sector.getCreatedBy().getId().equals(effectiveCompanyOwnerId)) {
			throw new IllegalArgumentException("O setor informado não pertence a empresa selecionada.");
		}

		return loadEligibleAssignees(sector).stream()
			.map(user -> new TicketTargetAssigneeResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail()
			))
			.toList();
	}

	@Transactional
	public TicketResponse create(CreateTicketRequest request, List<MultipartFile> files) {
		User requester = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.requesterEmail()))
			.orElseThrow(() -> new NotFoundException("Solicitante não encontrado."));
		TicketStatus status = ticketStatusRepository.findByCode("OPEN")
			.orElseThrow(() -> new NotFoundException("Status padrão de abertura não encontrado."));
		TicketPriority priority = ticketPriorityRepository.findByCode(request.priorityCode().trim().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new NotFoundException("Prioridade não encontrada."));
		com.helpdesk.helpdesk.domain.Sector sector = sectorRepository.findById(request.sectorId())
			.orElseThrow(() -> new NotFoundException("Setor não encontrado."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			sector.getCreatedBy().getId(),
			"O setor informado não pertence ao tenant atual."
		);

		User requesterCompany = resolveRequesterCompany(requester);
		UUID effectiveCompanyOwnerId = tenantAccessService.getCurrentTenantOwnerUserId()
			.orElse(request.companyOwnerId());
		if (!sector.getCreatedBy().getId().equals(effectiveCompanyOwnerId)) {
			throw new IllegalArgumentException("O setor informado não pertence a empresa selecionada.");
		}
		ensureAcceptedPartnership(requesterCompany, sector.getCreatedBy());
		String initialDescription = normalizeMessage(request.description());

		Ticket ticket = new Ticket();
		ticket.setTitle(buildAutoTicketTitle(initialDescription));
		ticket.setDescription(initialDescription);
		ticket.setRequester(requester);
		ticket.setAssignedTo(resolveAssignee(sector, request.assignedToUserId()));
		ticket.setSector(sector);
		ticket.setStatus(status);
		ticket.setPriority(priority);
		ticket.setChannel(TicketChannel.PORTAL);
		ticket.setCopyEmail(normalizeOptionalEmail(request.copyEmail()));

		Ticket savedTicket = saveTicketWithUniqueProtocol(ticket);
		createAssignmentNotification(savedTicket);
		notifyAssigneeAboutNewTicket(savedTicket);
		TicketMessage initialMessage = ensureInitialMessage(savedTicket);
		saveAttachments(savedTicket, initialMessage, requester, files);

		return toResponse(savedTicket);
	}

	@Transactional
	public Ticket createFromWhatsapp(CreateWhatsappTicketRequest request) {
		User requester = request.requester();
		List<IncomingAttachment> incomingAttachments = normalizeIncomingAttachments(request.attachments());
		TicketStatus status = ticketStatusRepository.findByCode("OPEN")
			.orElseThrow(() -> new NotFoundException("Status padrão de abertura não encontrado."));
		TicketPriority priority = ticketPriorityRepository.findByCode("MEDIUM")
			.orElseThrow(() -> new NotFoundException("Prioridade padrão não encontrada."));
		com.helpdesk.helpdesk.domain.Sector sector = sectorRepository.findById(request.sectorId())
			.orElseThrow(() -> new NotFoundException("Setor não encontrado."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			sector.getCreatedBy().getId(),
			"O setor informado não pertence ao tenant atual."
		);

		UUID effectiveCompanyOwnerId = tenantAccessService.getCurrentTenantOwnerUserId()
			.orElse(request.companyOwnerId());
		if (!sector.getCreatedBy().getId().equals(effectiveCompanyOwnerId)) {
			throw new IllegalArgumentException("O setor informado não pertence a empresa selecionada.");
		}
		if (sector.getCreatedBy().getCompanyType() != CompanyType.RESPONDER) {
			throw new IllegalArgumentException("O chamado só pode ser enviado para empresas que respondem chamados.");
		}
		String initialDescription = resolveWhatsappInboundMessage(request.description(), incomingAttachments);

		Ticket ticket = new Ticket();
		ticket.setTitle(buildAutoTicketTitle(initialDescription));
		ticket.setDescription(initialDescription);
		ticket.setRequester(requester);
		ticket.setAssignedTo(resolveAssignee(sector, request.assignedToUserId()));
		ticket.setSector(sector);
		ticket.setStatus(status);
		ticket.setPriority(priority);
		ticket.setChannel(TicketChannel.WHATSAPP);
		ticket.setCopyEmail(normalizeOptionalEmail(requester.getEmail()));

		Ticket savedTicket = saveTicketWithUniqueProtocol(ticket);
		createAssignmentNotification(savedTicket);
		notifyAssigneeAboutNewTicket(savedTicket);
		TicketMessage initialMessage = ensureInitialMessage(savedTicket);
		saveIncomingAttachments(savedTicket, initialMessage, requester, incomingAttachments);

		return savedTicket;
	}

	@Transactional
	public TicketResponse updateTitle(UUID ticketId, UpdateTicketTitleRequest request) {
		User author = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.authorEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela edição do título não encontrado."));
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, author.getEmail());

		ensureTitleCanBeUpdated(ticket, author);
		ticket.setTitle(normalizeTitle(request.title()));

		return toResponse(ticketRepository.save(ticket));
	}

	@Transactional
	public TicketResponse requestTransfer(UUID ticketId, RequestTicketTransferRequest request) {
		User author = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.authorEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela transferência não encontrado."));
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, author.getEmail());

		ensureTransferCanBeRequested(ticket, author);

		User recipient = userRepository.findById(request.recipientUserId())
			.orElseThrow(() -> new NotFoundException("Destinatário da transferência não encontrado."));
		boolean isValidRecipient = loadTransferCandidates(ticket, author).stream()
			.anyMatch(candidate -> candidate.getId().equals(recipient.getId()));

		if (!isValidRecipient) {
			throw new IllegalArgumentException("Selecione um destinatário válido da empresa para receber a transferência.");
		}

		OffsetDateTime requestedAt = OffsetDateTime.now();
		ticket.setPendingTransferRequestedBy(author);
		ticket.setPendingTransferTo(recipient);
		ticket.setPendingTransferRequestedAt(requestedAt);

		Ticket savedTicket = ticketRepository.save(ticket);

		TicketTransferNotification notification = new TicketTransferNotification();
		notification.setTicket(savedTicket);
		notification.setSender(author);
		notification.setRecipient(recipient);
		ticketTransferNotificationRepository.save(notification);

		return toResponse(savedTicket);
	}

	@Transactional
	public List<TicketMessageResponse> listMessages(UUID ticketId, String email) {
		User viewer = loadCurrentUserByEmail(email, "Usuário responsável pela consulta não encontrado.");
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, viewer.getEmail());
		ensureInitialMessage(ticket);
		Map<UUID, List<TicketAttachmentResponse>> attachmentsByMessageId = loadAttachmentsByMessageId(ticketId);

		return ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
			.map(message -> toMessageResponse(message, attachmentsByMessageId))
			.toList();
	}

	@Transactional
	public TicketMessageResponse addMessage(UUID ticketId, CreateTicketMessageRequest request, List<MultipartFile> files) {
		User author = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.authorEmail()))
			.orElseThrow(() -> new NotFoundException("Autor da mensagem não encontrado."));
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, author.getEmail());
		List<MultipartFile> validFiles = normalizeFiles(files);
		String normalizedMessage = normalizeMessage(request.message());

		ensureInitialMessage(ticket);

		if (normalizedMessage.isBlank() && validFiles.isEmpty()) {
			throw new IllegalArgumentException("Envie uma mensagem ou anexe ao menos um arquivo.");
		}

		TicketMessage ticketMessage = new TicketMessage();
		ticketMessage.setTicket(ticket);
		ticketMessage.setAuthor(author);
		ticketMessage.setMessage(normalizedMessage.isBlank() ? "Arquivo anexado." : normalizedMessage);
		ticketMessage.setInternal(false);

		TicketMessage savedMessage = ticketMessageRepository.save(ticketMessage);
		List<TicketAttachmentResponse> attachments = saveAttachments(ticket, savedMessage, author, validFiles);

		if (shouldMirrorMessageToWhatsapp(ticket, author)) {
			List<WhatsappService.OutboundAttachment> outboundAttachments = loadWhatsappOutboundAttachments(savedMessage.getId());
			whatsappService.sendMessage(
				resolveWhatsappCompanyOwner(ticket),
				resolveWhatsappTicketRecipient(ticket),
				buildWhatsappOutboundText(ticket.getProtocol(), author.getFullName(), savedMessage.getMessage(), outboundAttachments),
				outboundAttachments
			);
		}

		boolean isRequesterSideAuthor = isRequesterSideAuthor(ticket, author);

		if (ticket.getFirstResponseAt() == null && !isRequesterSideAuthor) {
			ticket.setFirstResponseAt(savedMessage.getCreatedAt());
		}

		if (!isRequesterSideAuthor && !"CLOSED".equalsIgnoreCase(ticket.getStatus().getCode())) {
			TicketStatus inProgressStatus = ticketStatusRepository.findByCode("IN_PROGRESS")
				.orElseThrow(() -> new NotFoundException("Status em andamento não encontrado."));
			ticket.setStatus(inProgressStatus);
		}

		if (!isRequesterSideAuthor) {
			hideActiveTicketNotifications(ticket.getId());
		}

		ticketRepository.save(ticket);
		createReplyNotification(ticket, savedMessage, author);

		return toMessageResponse(savedMessage, attachments);
	}

	@Transactional
	public TicketMessageResponse addWhatsappMessage(UUID ticketId, String message) {
		return addWhatsappMessage(ticketId, message, List.of());
	}

	@Transactional
	public TicketMessageResponse addWhatsappMessage(UUID ticketId, String message, List<IncomingAttachment> attachments) {
		Ticket ticket = ticketRepository.findById(ticketId)
			.orElseThrow(() -> new NotFoundException("Chamado não encontrado."));
		String normalizedMessage = normalizeMessage(message);
		List<IncomingAttachment> incomingAttachments = normalizeIncomingAttachments(attachments);

		if (normalizedMessage.isBlank() && incomingAttachments.isEmpty()) {
			throw new IllegalArgumentException("Envie uma mensagem de texto ou anexe um arquivo para continuar o atendimento.");
		}

		ensureInitialMessage(ticket);

		TicketMessage ticketMessage = new TicketMessage();
		ticketMessage.setTicket(ticket);
		ticketMessage.setAuthor(ticket.getRequester());
		ticketMessage.setMessage(resolveWhatsappInboundMessage(normalizedMessage, incomingAttachments));
		ticketMessage.setInternal(false);

		TicketMessage savedMessage = ticketMessageRepository.save(ticketMessage);
		List<TicketAttachmentResponse> savedAttachments = saveIncomingAttachments(
			ticket,
			savedMessage,
			ticket.getRequester(),
			incomingAttachments
		);
		createReplyNotification(ticket, savedMessage, ticket.getRequester());
		return toMessageResponse(savedMessage, savedAttachments);
	}

	@Transactional
	public TicketResponse closeTicket(UUID ticketId, CloseTicketRequest request) {
		User author = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.authorEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pelo fechamento não encontrado."));
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, author.getEmail());

		ensureTicketCanBeClosed(author);
		return toResponse(closeTicketInternal(ticket, author, false));
	}

	@Transactional
	public void deleteTickets(DeleteTicketsRequest request) {
		User author = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.authorEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela exclusão não encontrado."));
		ensureTicketCanBeDeleted(author);

		List<UUID> ticketIds = request.ticketIds().stream()
			.distinct()
			.toList();

		if (ticketIds.isEmpty()) {
			throw new IllegalArgumentException("Selecione ao menos um chamado para excluir.");
		}

		for (UUID ticketId : ticketIds) {
			Ticket ticket = loadDetailedAccessibleTicket(ticketId, author.getEmail());
			Ticket managedTicket = isTicketClosed(ticket)
				? ticket
				: closeTicketInternal(ticket, author, true);

			purgeManagedAttachments(managedTicket.getId());
			managedTicket.setDeletedAt(OffsetDateTime.now());
			managedTicket.setPendingTransferTo(null);
			managedTicket.setPendingTransferRequestedBy(null);
			managedTicket.setPendingTransferRequestedAt(null);
			ticketRepository.save(managedTicket);
			auditTrailService.recordUserAction("TICKET_DELETED", author, "ticket", managedTicket.getId());
			hideRelatedTicketNotifications(managedTicket.getId());
			clearWhatsappConversationForTicket(managedTicket.getId());
		}
	}

	private void ensureTicketCanBeClosed(User author) {
		if (!hasRole(author, "admin") && !hasRole(author, "employee")) {
			throw new IllegalArgumentException("Apenas administradores e funcionários podem fechar chamados.");
		}
	}

	private void ensureTicketCanBeDeleted(User author) {
		if (!hasRole(author, "admin")) {
			throw new IllegalArgumentException("Apenas administradores podem excluir chamados.");
		}
	}

	private void ensureTitleCanBeUpdated(Ticket ticket, User author) {
		if (hasRole(author, "admin")) {
			return;
		}

		if (!hasRole(author, "employee")) {
			throw new IllegalArgumentException("Apenas o administrador ou o funcionário responsável podem alterar o título do chamado.");
		}

		if (ticket.getAssignedTo() == null || !ticket.getAssignedTo().getId().equals(author.getId())) {
			throw new IllegalArgumentException("Somente o administrador ou o funcionário atualmente responsável podem alterar o título do chamado.");
		}
	}

	private boolean isTicketClosed(Ticket ticket) {
		return ticket != null
			&& ticket.getStatus() != null
			&& "CLOSED".equalsIgnoreCase(ticket.getStatus().getCode());
	}

	private Ticket closeTicketInternal(Ticket ticket, User author, boolean notifyRequesterOnClosure) {
		if (isTicketClosed(ticket)) {
			return ticket;
		}

		TicketStatus closedStatus = ticketStatusRepository.findByCode("CLOSED")
			.orElseThrow(() -> new NotFoundException("Status de fechamento não encontrado."));
		OffsetDateTime closedAt = OffsetDateTime.now();

		ticket.setStatus(closedStatus);
		ticket.setResolvedAt(ticket.getResolvedAt() == null ? closedAt : ticket.getResolvedAt());
		ticket.setClosedAt(closedAt);

		Ticket savedTicket = ticketRepository.save(ticket);

		if (notifyRequesterOnClosure) {
			createClosureNotification(savedTicket, author);
		}

		notifyWhatsappTicketClosure(savedTicket, author);
		ticketClosureEmailService.sendConversationTranscript(
			savedTicket,
			ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(savedTicket.getId()),
			loadAttachmentEntitiesByMessageId(savedTicket.getId())
		);

		return savedTicket;
	}

	private void createClosureNotification(Ticket ticket, User closedBy) {
		if (ticket == null || closedBy == null || ticket.getRequester() == null) {
			return;
		}

		if (closedBy.getId().equals(ticket.getRequester().getId())) {
			return;
		}

		TicketClosureNotification notification = new TicketClosureNotification();
		notification.setTicket(ticket);
		notification.setRecipient(ticket.getRequester());
		notification.setClosedBy(closedBy);
		ticketClosureNotificationRepository.save(notification);
	}

	private void hideRelatedTicketNotifications(UUID ticketId) {
		hideAssignmentNotifications(ticketId);

		List<TicketTransferNotification> transferNotifications = ticketTransferNotificationRepository.findByTicketId(ticketId);
		for (TicketTransferNotification notification : transferNotifications) {
			notification.setHidden(true);
		}
		if (!transferNotifications.isEmpty()) {
			ticketTransferNotificationRepository.saveAll(transferNotifications);
		}

		hideReplyNotifications(ticketId);
	}

	private void hideActiveTicketNotifications(UUID ticketId) {
		hideAssignmentNotifications(ticketId);
		hideReplyNotifications(ticketId);
	}

	private void hideAssignmentNotifications(UUID ticketId) {
		List<TicketAssignmentNotification> assignmentNotifications = ticketAssignmentNotificationRepository.findByTicketId(ticketId);
		for (TicketAssignmentNotification notification : assignmentNotifications) {
			notification.setHidden(true);
		}
		if (!assignmentNotifications.isEmpty()) {
			ticketAssignmentNotificationRepository.saveAll(assignmentNotifications);
		}
	}

	private void hideReplyNotifications(UUID ticketId) {
		List<TicketReplyNotification> replyNotifications = ticketReplyNotificationRepository.findByTicketId(ticketId);
		for (TicketReplyNotification notification : replyNotifications) {
			notification.setHidden(true);
		}
		if (!replyNotifications.isEmpty()) {
			ticketReplyNotificationRepository.saveAll(replyNotifications);
		}
	}

	private void clearWhatsappConversationForTicket(UUID ticketId) {
		whatsappConversationRepository.findByActiveTicketId(ticketId).ifPresent(conversation -> {
			conversation.setActiveTicket(null);
			whatsappConversationRepository.save(conversation);
		});
	}

	@Transactional(readOnly = true)
	public AttachmentDownload downloadAttachment(UUID ticketId, UUID attachmentId, String email) {
		User viewer = loadCurrentUserByEmail(email, "Usuário responsável pelo download não encontrado.");
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, viewer.getEmail());
		TicketAttachment attachment = loadAttachment(ticket.getId(), attachmentId);
		Resource resource = ticketAttachmentStorageService.loadAsResource(attachment.getStorageKey());
		auditTrailService.recordUserAction("TICKET_ATTACHMENT_DOWNLOADED", viewer, "ticket-attachment", attachment.getId());

		return new AttachmentDownload(
			resource,
			attachment.getOriginalFileName(),
			attachment.getContentType(),
			attachment.getSizeBytes()
		);
	}

	@Transactional(readOnly = true)
	public AttachmentDownload downloadPublicAttachment(UUID ticketId, UUID attachmentId) {
		TicketAttachment attachment = loadAttachment(ticketId, attachmentId);
		Resource resource = ticketAttachmentStorageService.loadAsResource(attachment.getStorageKey());

		return new AttachmentDownload(
			resource,
			attachment.getOriginalFileName(),
			attachment.getContentType(),
			attachment.getSizeBytes()
		);
	}

	private TicketAttachment loadAttachment(UUID ticketId, UUID attachmentId) {
		return ticketAttachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
			.orElseThrow(() -> new NotFoundException("Anexo não encontrado para este chamado."));
	}

	private Ticket saveTicketWithUniqueProtocol(Ticket ticket) {
		for (int attempt = 1; attempt <= 5; attempt++) {
			ticket.setProtocol(nextProtocol());
			try {
				return ticketRepository.saveAndFlush(ticket);
			} catch (DataIntegrityViolationException exception) {
				if (!isProtocolConflict(exception) || attempt == 5) {
					throw exception;
				}

				logger.warn(
					"Colisão de protocolo detectada ao criar chamado. Tentando novamente com novo protocolo. tentativa={}, canal={}",
					attempt,
					ticket.getChannel()
				);
			}
		}

		throw new IllegalStateException("Não foi possível gerar um protocolo único para o chamado.");
	}

	private boolean isProtocolConflict(DataIntegrityViolationException exception) {
		String message = buildExceptionMessage(exception).toLowerCase(Locale.ROOT);
		return message.contains("tickets_protocol_key") || (message.contains("protocol") && message.contains("duplicate"));
	}

	private String buildExceptionMessage(Throwable throwable) {
		StringBuilder builder = new StringBuilder();
		Throwable current = throwable;
		int depth = 0;
		while (current != null && depth < 10) {
			if (current.getMessage() != null && !current.getMessage().isBlank()) {
				if (!builder.isEmpty()) {
					builder.append(' ');
				}
				builder.append(current.getMessage());
			}
			current = current.getCause();
			depth++;
		}
		return builder.toString();
	}

	private String nextProtocol() {
		int currentYear = Year.now().getValue();
		String prefix = "CA-" + currentYear + "-";
		long nextNumber = ticketRepository.findMaxProtocolSequenceByPrefix(prefix) + 1;
		return prefix + String.format("%04d", nextNumber);
	}

	private TicketResponse toResponse(Ticket ticket) {
		DisplayStatus displayStatus = resolveDisplayStatus(ticket);
		User requesterCompany = resolveRequesterCompanyForDisplay(ticket.getRequester());
		return new TicketResponse(
			ticket.getId(),
			ticket.getProtocol(),
			ticket.getTitle(),
			ticket.getDescription(),
			ticket.getRequester().getFullName(),
			ticket.getRequester().getEmail(),
			ticket.getRequester().getPhoneNumber(),
			ticket.getRequester().getDocumentNumber(),
			requesterCompany == null ? null : resolveCompanyName(requesterCompany),
			ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getFullName(),
			ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getEmail(),
			ticket.getSector().getName(),
			resolveChannel(ticket).name(),
			displayStatus.code(),
			displayStatus.name(),
			ticket.getPriority().getCode(),
			ticket.getPriority().getName(),
			ticket.getOpenedAt(),
			ticket.getClosedAt(),
			ticket.getPendingTransferTo() == null ? null : ticket.getPendingTransferTo().getFullName()
		);
	}

	private User resolveRequesterCompanyForDisplay(User requester) {
		if (requester == null) {
			return null;
		}

		if (hasRole(requester, "ADMIN")
			&& requester.getCompanyName() != null
			&& !requester.getCompanyName().isBlank()) {
			return requester;
		}

		if (requester.getCompanyOwner() != null) {
			return requester.getCompanyOwner();
		}

		return null;
	}

	private String resolveCompanyName(User companyOwner) {
		if (companyOwner == null) {
			return null;
		}
		if (companyOwner.getCompanyName() != null && !companyOwner.getCompanyName().isBlank()) {
			return companyOwner.getCompanyName().trim();
		}
		return companyOwner.getFullName();
	}

	private DisplayStatus resolveDisplayStatus(Ticket ticket) {
		if ("CLOSED".equalsIgnoreCase(ticket.getStatus().getCode())) {
			return new DisplayStatus("CLOSED", "Fechado");
		}

		if (ticket.getPendingTransferTo() != null) {
			return new DisplayStatus(
				"IN_PROGRESS_TRANSFER_PENDING",
				"Em andamento - chamado transferido para " + ticket.getPendingTransferTo().getFullName()
			);
		}

		TicketMessage lastMessage = ticketMessageRepository.findFirstByTicketIdOrderByCreatedAtDesc(ticket.getId())
			.orElse(null);

		if (lastMessage == null) {
			return new DisplayStatus("OPEN", "Aberto");
		}

		boolean isRequesterLastAuthor = isRequesterSideAuthor(ticket, lastMessage.getAuthor());

		if (isRequesterLastAuthor && ticketMessageRepository.countByTicketId(ticket.getId()) <= 1) {
			return new DisplayStatus("OPEN", "Aberto");
		}

		if (isRequesterLastAuthor) {
			return new DisplayStatus(
				"IN_PROGRESS_REQUESTER_REPLY",
				"Em andamento - replica de " + lastMessage.getAuthor().getFullName()
			);
		}

		return new DisplayStatus(
			"IN_PROGRESS",
			"Em andamento - respondido por " + lastMessage.getAuthor().getFullName()
		);
	}

	private List<User> loadTransferCandidates(Ticket ticket, User author) {
		Map<UUID, User> candidatesById = new LinkedHashMap<>();

		for (SectorMember member : sectorMemberRepository.findActiveEmployeesByCompanyOwnerIdOrderByUserFullNameAsc(
			ticket.getSector().getCreatedBy().getId()
		)) {
			User candidate = member.getUser();

			if (candidate == null || candidate.getId().equals(author.getId())) {
				continue;
			}

			candidatesById.putIfAbsent(candidate.getId(), candidate);
		}

		User companyAdmin = ticket.getSector().getCreatedBy();
		if (companyAdmin != null
			&& !companyAdmin.getId().equals(author.getId())
			&& companyAdmin.getStatus() != null
			&& companyAdmin.getStatus().name().equalsIgnoreCase("ACTIVE")
			&& hasRole(companyAdmin, "admin")) {
			candidatesById.putIfAbsent(companyAdmin.getId(), companyAdmin);
		}

		return candidatesById.values().stream()
			.sorted(Comparator.comparing(user -> user.getFullName().toLowerCase(Locale.ROOT)))
			.toList();
	}

	private void ensureTransferCanBeRequested(Ticket ticket, User author) {
		if ("CLOSED".equalsIgnoreCase(ticket.getStatus().getCode())) {
			throw new IllegalArgumentException("Não é possível transferir um chamado já fechado.");
		}

		if (hasRole(author, "admin")) {
			if (ticket.getPendingTransferTo() != null
				|| ticketTransferNotificationRepository.existsByTicketIdAndStatus(ticket.getId(), TicketTransferStatus.PENDING)) {
				throw new IllegalArgumentException("Esse chamado já possui uma transferência pendente.");
			}
			return;
		}

		if (!hasRole(author, "employee")) {
			throw new IllegalArgumentException("Apenas administradores e funcionários podem transferir chamados.");
		}

		if (ticket.getAssignedTo() == null || !ticket.getAssignedTo().getId().equals(author.getId())) {
			throw new IllegalArgumentException("Somente o administrador ou o funcionário atualmente responsável pode transferir o chamado.");
		}

		if (ticket.getPendingTransferTo() != null
			|| ticketTransferNotificationRepository.existsByTicketIdAndStatus(ticket.getId(), TicketTransferStatus.PENDING)) {
			throw new IllegalArgumentException("Esse chamado já possui uma transferência pendente.");
		}
	}

	private Ticket loadDetailedAccessibleTicket(UUID ticketId, String email) {
		User viewer = loadCurrentUserByEmail(email, "Usuário responsável pela consulta não encontrado.");
		return loadDetailedAccessibleTicket(ticketId, viewer);
	}

	private Ticket loadDetailedAccessibleTicket(UUID ticketId, User viewer) {
		if (isTenantOwnerAdmin(viewer)) {
			return ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
				.orElseThrow(() -> new NotFoundException("Chamado não encontrado ou indisponível para esse usuário."));
		}

		return ticketRepository.findDetailedVisibleByIdAndEmail(ticketId, normalizeEmail(viewer.getEmail()))
			.orElseThrow(() -> new NotFoundException("Chamado não encontrado ou indisponível para esse usuário."));
	}

	private User loadCurrentUserByEmail(String email, String notFoundMessage) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException(notFoundMessage));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");
		return user;
	}

	private List<Ticket> loadVisibleTickets(User viewer, List<String> statusCodes) {
		if (isTenantOwnerAdmin(viewer)) {
			Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
			return statusCodes == null || statusCodes.isEmpty()
				? ticketRepository.findByDeletedAtIsNull(sort)
				: ticketRepository.findByDeletedAtIsNullAndStatusCodeIn(statusCodes, sort);
		}

		String normalizedEmail = normalizeEmail(viewer.getEmail());
		return statusCodes == null || statusCodes.isEmpty()
			? ticketRepository.findVisibleByEmailOrderByCreatedAtDesc(normalizedEmail)
			: ticketRepository.findVisibleByEmailAndStatusCodesOrderByCreatedAtDesc(normalizedEmail, statusCodes);
	}

	private boolean isTenantOwnerAdmin(User viewer) {
		return viewer != null
			&& viewer.getId() != null
			&& hasRole(viewer, "admin")
			&& tenantAccessService.getCurrentTenantOwnerUserId()
				.map(ownerUserId -> ownerUserId.equals(viewer.getId()))
				.orElse(false);
	}

	private User resolveAssignee(com.helpdesk.helpdesk.domain.Sector sector, UUID assignedToUserId) {
		List<User> eligibleAssignees = loadEligibleAssignees(sector);
		if (eligibleAssignees.isEmpty()) {
			throw new IllegalArgumentException("Esse setor não possui funcionários disponíveis para receber chamados.");
		}

		if (assignedToUserId == null) {
			return resolveNextAssignee(sector, eligibleAssignees);
		}

		return eligibleAssignees.stream()
			.filter(user -> user.getId().equals(assignedToUserId))
			.findFirst()
			.orElseThrow(() ->
				new IllegalArgumentException("Selecione um funcionário válido desse setor para receber o chamado."));
	}

	private List<User> loadEligibleAssignees(com.helpdesk.helpdesk.domain.Sector sector) {
		List<User> eligibleEmployees = sectorMemberRepository.findBySectorIdOrderByAssignedAtAsc(sector.getId()).stream()
			.map(SectorMember::getUser)
			.filter(java.util.Objects::nonNull)
			.filter(user -> user.getStatus() != null)
			.filter(user -> user.getStatus().name().equalsIgnoreCase("ACTIVE"))
			.filter(user -> hasRole(user, "employee"))
			.toList();

		if (!eligibleEmployees.isEmpty()) {
			return eligibleEmployees;
		}

		User companyAdmin = sector.getCreatedBy();
		if (companyAdmin != null
			&& companyAdmin.getStatus() != null
			&& companyAdmin.getStatus().name().equalsIgnoreCase("ACTIVE")
			&& hasRole(companyAdmin, "admin")) {
			return List.of(companyAdmin);
		}

		return List.of();
	}

	private User resolveNextAssignee(com.helpdesk.helpdesk.domain.Sector sector, List<User> eligibleAssignees) {
		List<UUID> eligibleUserIds = eligibleAssignees.stream()
			.map(User::getId)
			.toList();

		UUID lastAssignedUserId = ticketRepository
			.findFirstBySectorIdAndAssignedToIdInOrderByCreatedAtDesc(sector.getId(), eligibleUserIds)
			.map(Ticket::getAssignedTo)
			.map(User::getId)
			.orElse(null);

		if (lastAssignedUserId == null) {
			return eligibleAssignees.get(0);
		}

		for (int index = 0; index < eligibleAssignees.size(); index++) {
			if (!eligibleAssignees.get(index).getId().equals(lastAssignedUserId)) {
				continue;
			}

			int nextIndex = (index + 1) % eligibleAssignees.size();
			return eligibleAssignees.get(nextIndex);
		}

		return eligibleAssignees.get(0);
	}

	private void createAssignmentNotification(Ticket ticket) {
		if (ticket.getAssignedTo() == null) {
			return;
		}

		createAssignmentNotificationForRecipient(ticket, ticket.getAssignedTo());
		User companyAdmin = resolveTicketCompanyAdmin(ticket);
		if (companyAdmin != null && !companyAdmin.getId().equals(ticket.getAssignedTo().getId())) {
			createAssignmentNotificationForRecipient(ticket, companyAdmin);
		}
	}

	private void notifyAssigneeAboutNewTicket(Ticket ticket) {
		if (ticket == null || ticket.getAssignedTo() == null || ticket.getRequester() == null) {
			return;
		}

		User assignee = ticket.getAssignedTo();
		if (assignee.getId() != null && assignee.getId().equals(ticket.getRequester().getId())) {
			return;
		}

		String whatsappRecipient = resolveUserWhatsappRecipientOrBlank(assignee);
		if (whatsappRecipient.isBlank()) {
			return;
		}

		String notificationMessage = buildAssigneeNewTicketWhatsappText(ticket);

		try {
			whatsappService.sendMessage(
				resolveWhatsappCompanyOwnerForAssigneeNotification(ticket),
				whatsappRecipient,
				notificationMessage
			);
		} catch (RuntimeException exception) {
			logger.warn(
				"Falha ao enviar mensagem de novo chamado para o responsável: ticketId={}, protocol={}, assigneeId={}, recipient={}",
				ticket.getId(),
				ticket.getProtocol(),
				assignee.getId(),
				whatsappRecipient,
				exception
			);
		}
	}

	private void createReplyNotification(Ticket ticket, TicketMessage message, User author) {
		if (ticket == null || message == null || author == null || ticket.getAssignedTo() == null) {
			return;
		}

		if (!isRequesterSideAuthor(ticket, author)) {
			return;
		}

		if ("CLOSED".equalsIgnoreCase(ticket.getStatus().getCode())) {
			return;
		}

		if (ticket.getAssignedTo().getId().equals(author.getId())) {
			return;
		}

		createReplyNotificationForRecipient(ticket, message, ticket.getAssignedTo());
		User companyAdmin = resolveTicketCompanyAdmin(ticket);
		if (companyAdmin != null && !companyAdmin.getId().equals(ticket.getAssignedTo().getId())) {
			createReplyNotificationForRecipient(ticket, message, companyAdmin);
		}
	}

	private void createAssignmentNotificationForRecipient(Ticket ticket, User recipient) {
		if (ticket == null || recipient == null) {
			return;
		}

		TicketAssignmentNotification notification = new TicketAssignmentNotification();
		notification.setTicket(ticket);
		notification.setRecipient(recipient);
		ticketAssignmentNotificationRepository.save(notification);
	}

	private void createReplyNotificationForRecipient(Ticket ticket, TicketMessage message, User recipient) {
		if (ticket == null || message == null || recipient == null) {
			return;
		}

		TicketReplyNotification notification = new TicketReplyNotification();
		notification.setTicket(ticket);
		notification.setMessage(message);
		notification.setRecipient(recipient);
		ticketReplyNotificationRepository.save(notification);
	}

	private User resolveTicketCompanyAdmin(Ticket ticket) {
		User companyAdmin = tenantAccessService.getCurrentTenantOwnerUserId()
			.flatMap(userRepository::findById)
			.orElseGet(() -> ticket == null || ticket.getSector() == null ? null : ticket.getSector().getCreatedBy());
		if (companyAdmin == null || companyAdmin.getStatus() == null) {
			return null;
		}

		if (!companyAdmin.getStatus().name().equalsIgnoreCase("ACTIVE") || !hasRole(companyAdmin, "admin")) {
			return null;
		}

		return companyAdmin;
	}

	private TicketMessage ensureInitialMessage(Ticket ticket) {
		if (ticketMessageRepository.existsByTicketId(ticket.getId())) {
			return ticketMessageRepository.findFirstByTicketIdOrderByCreatedAtAsc(ticket.getId())
				.orElseThrow(() -> new NotFoundException("Mensagem inicial do chamado não encontrada."));
		}

		TicketMessage initialMessage = new TicketMessage();
		initialMessage.setTicket(ticket);
		initialMessage.setAuthor(ticket.getRequester());
		initialMessage.setMessage(ticket.getDescription());
		initialMessage.setInternal(false);
		initialMessage.setCreatedAt(ticket.getOpenedAt() == null ? OffsetDateTime.now() : ticket.getOpenedAt());
		return ticketMessageRepository.save(initialMessage);
	}

	private Map<UUID, List<TicketAttachmentResponse>> loadAttachmentsByMessageId(UUID ticketId) {
		Map<UUID, List<TicketAttachmentResponse>> attachmentsByMessageId = new LinkedHashMap<>();

		for (TicketAttachment attachment : ticketAttachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)) {
			if (attachment.getMessage() == null) {
				continue;
			}

			attachmentsByMessageId
				.computeIfAbsent(attachment.getMessage().getId(), ignored -> new java.util.ArrayList<>())
				.add(toAttachmentResponse(attachment));
		}

		return attachmentsByMessageId;
	}

	private Map<UUID, List<TicketAttachment>> loadAttachmentEntitiesByMessageId(UUID ticketId) {
		Map<UUID, List<TicketAttachment>> attachmentsByMessageId = new LinkedHashMap<>();

		for (TicketAttachment attachment : ticketAttachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)) {
			if (attachment.getMessage() == null) {
				continue;
			}

			attachmentsByMessageId
				.computeIfAbsent(attachment.getMessage().getId(), ignored -> new java.util.ArrayList<>())
				.add(attachment);
		}

		return attachmentsByMessageId;
	}

	private List<TicketAttachmentResponse> saveAttachments(
		Ticket ticket,
		TicketMessage message,
		User uploadedBy,
		List<MultipartFile> files
	) {
		List<MultipartFile> validFiles = normalizeFiles(files);

		if (validFiles.isEmpty()) {
			return List.of();
		}

		return validFiles.stream()
			.map(file -> saveAttachment(ticket, message, uploadedBy, file))
			.map(this::toAttachmentResponse)
			.toList();
	}

	private List<TicketAttachmentResponse> saveIncomingAttachments(
		Ticket ticket,
		TicketMessage message,
		User uploadedBy,
		List<IncomingAttachment> attachments
	) {
		List<IncomingAttachment> validAttachments = normalizeIncomingAttachments(attachments);

		if (validAttachments.isEmpty()) {
			return List.of();
		}

		return validAttachments.stream()
			.map(attachment -> saveIncomingAttachment(ticket, message, uploadedBy, attachment))
			.map(this::toAttachmentResponse)
			.toList();
	}

	private TicketAttachment saveAttachment(Ticket ticket, TicketMessage message, User uploadedBy, MultipartFile file) {
		if (file.getSize() <= 0) {
			throw new IllegalArgumentException("Os anexos enviados devem conter conteúdo.");
		}

		TicketAttachmentStorageService.StoredAttachment storedAttachment = ticketAttachmentStorageService.store(file);
		TicketAttachment attachment = new TicketAttachment();
		attachment.setTicket(ticket);
		attachment.setMessage(message);
		attachment.setUploadedBy(uploadedBy);
		attachment.setOriginalFileName(storedAttachment.originalFileName());
		attachment.setStorageKey(storedAttachment.storageKey());
		attachment.setContentType(storedAttachment.contentType());
		attachment.setSizeBytes(storedAttachment.sizeBytes());

		return ticketAttachmentRepository.save(attachment);
	}

	private TicketAttachment saveIncomingAttachment(
		Ticket ticket,
		TicketMessage message,
		User uploadedBy,
		IncomingAttachment attachment
	) {
		if (attachment.content() == null || attachment.content().length == 0) {
			throw new IllegalArgumentException("Os anexos enviados devem conter conteúdo.");
		}

		TicketAttachmentStorageService.StoredAttachment storedAttachment = ticketAttachmentStorageService.store(
			attachment.originalFileName(),
			attachment.contentType(),
			attachment.content()
		);
		TicketAttachment savedAttachment = new TicketAttachment();
		savedAttachment.setTicket(ticket);
		savedAttachment.setMessage(message);
		savedAttachment.setUploadedBy(uploadedBy);
		savedAttachment.setOriginalFileName(storedAttachment.originalFileName());
		savedAttachment.setStorageKey(storedAttachment.storageKey());
		savedAttachment.setContentType(storedAttachment.contentType());
		savedAttachment.setSizeBytes(storedAttachment.sizeBytes());

		return ticketAttachmentRepository.save(savedAttachment);
	}

	private void purgeManagedAttachments(UUID ticketId) {
		List<TicketAttachment> attachments = ticketAttachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
		for (TicketAttachment attachment : attachments) {
			ticketAttachmentStorageService.deleteIfManaged(attachment.getStorageKey());
		}
		if (!attachments.isEmpty()) {
			ticketAttachmentRepository.deleteAll(attachments);
		}
	}

	private TicketMessageResponse toMessageResponse(
		TicketMessage message,
		Map<UUID, List<TicketAttachmentResponse>> attachmentsByMessageId
	) {
		return toMessageResponse(
			message,
			attachmentsByMessageId.getOrDefault(message.getId(), List.of())
		);
	}

	private TicketMessageResponse toMessageResponse(TicketMessage message, List<TicketAttachmentResponse> attachments) {
		return new TicketMessageResponse(
			message.getId(),
			message.getAuthor().getFullName(),
			message.getAuthor().getEmail(),
			getPrimaryRoleLabel(message.getAuthor()),
			message.getMessage(),
			message.isInternal(),
			attachments,
			message.getCreatedAt()
		);
	}

	private TicketAttachmentResponse toAttachmentResponse(TicketAttachment attachment) {
		return new TicketAttachmentResponse(
			attachment.getId(),
			attachment.getOriginalFileName(),
			attachment.getContentType(),
			attachment.getSizeBytes(),
			attachment.getUploadedBy().getFullName(),
			attachment.getUploadedBy().getEmail(),
			attachment.getCreatedAt()
		);
	}

	private String getPrimaryRoleLabel(User user) {
		if (hasRole(user, "admin")) {
			return "Administrador";
		}

		if (hasRole(user, "employee")) {
			return "Funcionário";
		}

		return "Usuário";
	}

	private List<IncomingAttachment> normalizeIncomingAttachments(List<IncomingAttachment> attachments) {
		if (attachments == null || attachments.isEmpty()) {
			return List.of();
		}

		return attachments.stream()
			.filter(java.util.Objects::nonNull)
			.filter(attachment -> attachment.content() != null && attachment.content().length > 0)
			.map(attachment -> new IncomingAttachment(
				normalizeIncomingAttachmentName(attachment.originalFileName(), attachment.contentType()),
				normalizeIncomingAttachmentContentType(attachment.contentType()),
				attachment.content()
			))
			.toList();
	}

	private String normalizeIncomingAttachmentName(String originalFileName, String contentType) {
		String normalizedName = originalFileName == null ? "" : originalFileName.trim();
		if (!normalizedName.isBlank()) {
			return normalizedName;
		}

		return switch (normalizeIncomingAttachmentContentType(contentType)) {
			case "image/jpeg" -> "imagem-whatsapp.jpg";
			case "image/png" -> "imagem-whatsapp.png";
			case "image/webp" -> "imagem-whatsapp.webp";
			case "video/mp4" -> "video-whatsapp.mp4";
			case "audio/ogg" -> "audio-whatsapp.ogg";
			case "audio/mpeg" -> "audio-whatsapp.mp3";
			case "application/pdf" -> "documento-whatsapp.pdf";
			default -> "arquivo-whatsapp";
		};
	}

	private String normalizeIncomingAttachmentContentType(String contentType) {
		String normalizedContentType = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
		return normalizedContentType.isBlank() ? "application/octet-stream" : normalizedContentType;
	}

	private String resolveWhatsappInboundMessage(String message, List<IncomingAttachment> attachments) {
		String normalizedMessage = normalizeMessage(message);
		if (!normalizedMessage.isBlank()) {
			return normalizedMessage;
		}

		if (attachments == null || attachments.isEmpty()) {
			return normalizedMessage;
		}

		return attachments.size() == 1
			? "Arquivo enviado via WhatsApp."
			: attachments.size() + " arquivos enviados via WhatsApp.";
	}

	private String buildAutoTicketTitle(String firstMessage) {
		String normalizedMessage = normalizeTitleSource(firstMessage);
		if (normalizedMessage.length() <= AUTO_TICKET_TITLE_PREVIEW_LENGTH) {
			return normalizeTitle(normalizedMessage);
		}
		String preview = normalizedMessage.substring(0, AUTO_TICKET_TITLE_PREVIEW_LENGTH);
		return normalizeTitle(preview + "...");
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream()
			.anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private User resolveRequesterCompany(User requester) {
		if (requester == null) {
			throw new IllegalArgumentException("O usuário responsável pelo chamado não foi encontrado.");
		}

		if (hasRole(requester, "ADMIN")
			&& requester.getCompanyName() != null
			&& requester.getCompanyDocument() != null) {
			return requester;
		}

		if (requester.getCompanyOwner() != null) {
			return requester.getCompanyOwner();
		}

		throw new IllegalArgumentException("O usuário precisa estar vinculado a uma empresa para abrir chamados.");
	}

	private void ensureAcceptedPartnership(User requesterCompany, User targetCompany) {
		if (requesterCompany == null || targetCompany == null) {
			throw new IllegalArgumentException("Não foi possível validar a parceria entre as empresas.");
		}

		if (!companyPartnershipRepository.existsByCompanyPairAndStatus(
			requesterCompany.getId(),
			targetCompany.getId(),
			CompanyPartnershipStatus.ACCEPTED
		)) {
			throw new IllegalArgumentException("Sua empresa ainda não possui uma parceria aceita com a empresa selecionada.");
		}
	}

	private boolean shouldMirrorMessageToWhatsapp(Ticket ticket, User author) {
		return resolveChannel(ticket) == TicketChannel.WHATSAPP
			&& ticket.getRequester() != null
			&& ticket.getSector() != null
			&& ticket.getSector().getCreatedBy() != null
			&& !resolveWhatsappTicketRecipient(ticket).isBlank()
			&& isResponderSideAuthor(ticket, author);
	}

	private boolean isRequesterSideAuthor(Ticket ticket, User author) {
		if (ticket == null || author == null || ticket.getRequester() == null) {
			return false;
		}

		if (isResponderSideAuthor(ticket, author)) {
			return false;
		}

		if (author.getId().equals(ticket.getRequester().getId())) {
			return true;
		}

		User requesterCompany = ticket.getRequester().getCompanyOwner();
		if (requesterCompany == null) {
			return false;
		}

		if (author.getId().equals(requesterCompany.getId())) {
			return true;
		}

		User authorCompany = author.getCompanyOwner();
		return authorCompany != null && requesterCompany.getId().equals(authorCompany.getId());
	}

	private boolean isResponderSideAuthor(Ticket ticket, User author) {
		if (ticket == null || author == null || ticket.getSector() == null || ticket.getSector().getCreatedBy() == null) {
			return false;
		}

		User responderCompany = ticket.getSector().getCreatedBy();
		if (author.getId().equals(responderCompany.getId())) {
			return true;
		}

		User authorCompany = author.getCompanyOwner();
		return authorCompany != null && authorCompany.getId().equals(responderCompany.getId());
	}

	private boolean hasWhatsappRecipient(User requester) {
		if (requester == null) {
			return false;
		}
		return (requester.getWhatsappTransportId() != null && !requester.getWhatsappTransportId().isBlank())
			|| (requester.getPhoneNumber() != null && !requester.getPhoneNumber().isBlank());
	}

	private String buildAssigneeNewTicketWhatsappText(Ticket ticket) {
		String protocol = ticket == null || ticket.getProtocol() == null || ticket.getProtocol().isBlank()
			? "nao informado"
			: ticket.getProtocol().trim();
		String companyName = resolveTicketResponderCompanyName(ticket);
		String requesterName = ticket == null || ticket.getRequester() == null || ticket.getRequester().getFullName() == null
			|| ticket.getRequester().getFullName().isBlank()
				? "um solicitante"
				: ticket.getRequester().getFullName().trim();
		String title = ticket == null || ticket.getTitle() == null || ticket.getTitle().isBlank()
			? "Chamado sem titulo"
			: ticket.getTitle().trim();

		return """
			Voce recebeu um novo chamado no ChamaQui da empresa %s.
			Protocolo: %s
			Solicitante: %s
			Titulo: %s

			Acesse o ChamaQui da empresa e responda o chamado assim que possivel.
			""".formatted(companyName, protocol, requesterName, title).trim();
	}

	private String buildWhatsappOutboundText(
		String protocol,
		String authorName,
		String message,
		List<WhatsappService.OutboundAttachment> attachments
	) {
		String normalizedProtocol = protocol == null || protocol.isBlank() ? "nao informado" : protocol.trim();
		String normalizedAuthorName = authorName == null || authorName.isBlank() ? "Atendente" : authorName.trim();
		String normalizedMessage = message == null ? "" : message.trim();
		if (normalizedMessage.isBlank()) {
			return attachments == null || attachments.isEmpty()
				? ""
				: "*%s diz para o protocolo %s:*".formatted(normalizedAuthorName, normalizedProtocol);
		}

		return ("*%s diz para o protocolo %s:* %s".formatted(
			normalizedAuthorName,
			normalizedProtocol,
			normalizedMessage
		)).trim();
	}

	private List<WhatsappService.OutboundAttachment> loadWhatsappOutboundAttachments(UUID messageId) {
		return ticketAttachmentRepository.findByMessageIdOrderByCreatedAtAsc(messageId).stream()
			.map(this::toWhatsappOutboundAttachment)
			.toList();
	}

	private WhatsappService.OutboundAttachment toWhatsappOutboundAttachment(TicketAttachment attachment) {
		Resource resource = ticketAttachmentStorageService.loadAsResource(attachment.getStorageKey());

		try (var inputStream = resource.getInputStream()) {
			return new WhatsappService.OutboundAttachment(
				attachment.getOriginalFileName(),
				attachment.getContentType(),
				Base64.getEncoder().encodeToString(inputStream.readAllBytes())
			);
		} catch (IOException exception) {
			throw new IllegalStateException("Não foi possível preparar o anexo para envio no WhatsApp.");
		}
	}

	private void notifyWhatsappTicketClosure(Ticket ticket, User closedBy) {
		if (ticket == null || closedBy == null) {
			return;
		}
		if (!"WHATSAPP".equalsIgnoreCase(resolveChannel(ticket).name())) {
			return;
		}
		String closureRecipient = resolveWhatsappClosureRecipient(ticket);
		if (closureRecipient.isBlank()) {
			return;
		}

		String closedByName = closedBy.getFullName() == null || closedBy.getFullName().isBlank()
			? "funcionario"
			: closedBy.getFullName().trim();
		String closureMessage = """
			Seu chamado foi encerrado.
			Protocolo: %s
			Encerrado por: %s

			Se precisar de um novo atendimento, envie uma nova mensagem.
			""".formatted(ticket.getProtocol(), closedByName).trim();

		try {
			whatsappService.sendMessage(
				resolveWhatsappCompanyOwner(ticket),
				closureRecipient,
				closureMessage
			);
		} catch (RuntimeException exception) {
			logger.warn(
				"Falha ao enviar mensagem de fechamento no WhatsApp: ticketId={}, protocol={}, recipient={}",
				ticket.getId(),
				ticket.getProtocol(),
				closureRecipient,
				exception
			);
		}
	}

	private String resolveWhatsappClosureRecipient(Ticket ticket) {
		return resolveWhatsappTicketRecipient(ticket);
	}

	private User resolveWhatsappCompanyOwnerForAssigneeNotification(Ticket ticket) {
		User companyAdmin = resolveTicketCompanyAdmin(ticket);
		if (companyAdmin != null) {
			return companyAdmin;
		}
		return resolveWhatsappCompanyOwner(ticket);
	}

	private String resolveWhatsappTicketRecipient(Ticket ticket) {
		if (ticket == null || ticket.getId() == null) {
			return "";
		}

		String requesterRecipient = hasWhatsappRecipient(ticket.getRequester())
			? resolveWhatsappRecipient(ticket.getRequester())
			: "";
		User companyOwner = resolveWhatsappCompanyOwner(ticket);

		String conversationRecipient = whatsappConversationRepository.findByActiveTicketId(ticket.getId())
			.map(conversation -> firstNonBlank(conversation.getWhatsappTransportId(), conversation.getPhoneNumber()))
			.filter(value -> value != null && !value.isBlank())
			.orElseGet(() -> {
				if (!requesterRecipient.isBlank()) {
					return whatsappConversationRepository.findByCompanyOwnerIdAndWhatsappTransportId(companyOwner.getId(), requesterRecipient)
						.map(conversation -> firstNonBlank(conversation.getWhatsappTransportId(), conversation.getPhoneNumber()))
						.filter(value -> value != null && !value.isBlank())
						.orElse("");
				}
				return "";
			});

		return firstNonBlank(conversationRecipient, requesterRecipient);
	}

	private String resolveUserWhatsappRecipientOrBlank(User user) {
		if (!hasWhatsappRecipient(user)) {
			return "";
		}

		try {
			return resolveWhatsappRecipient(user);
		} catch (IllegalArgumentException exception) {
			logger.warn(
				"Usuario configurado sem destinatario valido para WhatsApp: userId={}, email={}",
				user == null ? null : user.getId(),
				user == null ? null : user.getEmail(),
				exception
			);
			return "";
		}
	}

	private String normalizeTitle(String title) {
		String normalizedTitle = normalizeTitleSource(title);
		if (normalizedTitle.length() > 180) {
			return normalizedTitle.substring(0, 180);
		}
		return normalizedTitle;
	}

	private String normalizeTitleSource(String value) {
		String normalizedValue = value == null ? "" : value.replaceAll("\\s+", " ").trim();
		if (normalizedValue.isBlank()) {
			return "Chamado";
		}
		return normalizedValue;
	}

	private User resolveWhatsappCompanyOwner(Ticket ticket) {
		if (ticket == null || ticket.getSector() == null || ticket.getSector().getCreatedBy() == null) {
			throw new IllegalArgumentException("O chamado do WhatsApp não possui uma empresa responsável configurada.");
		}
		return ticket.getSector().getCreatedBy();
	}

	private String resolveTicketResponderCompanyName(Ticket ticket) {
		User companyOwner = resolveWhatsappCompanyOwnerForAssigneeNotification(ticket);
		if (companyOwner == null) {
			return "nao informada";
		}
		return resolveCompanyName(companyOwner);
	}

	private TicketChannel resolveChannel(Ticket ticket) {
		return ticket.getChannel() == null ? TicketChannel.PORTAL : ticket.getChannel();
	}

	private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			return List.of();
		}

		return files.stream()
			.filter(file -> file != null && !file.isEmpty())
			.toList();
	}

	private String normalizeMessage(String message) {
		if (message == null) {
			return "";
		}

		String normalizedMessage = message.trim();

		if (normalizedMessage.length() > 5000) {
			throw new IllegalArgumentException("A mensagem deve ter no máximo 5000 caracteres.");
		}

		return normalizedMessage;
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe o email do usuário.");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeOptionalEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizePhone(String phone) {
		String normalizedPhone = phone == null ? "" : phone.replaceAll("\\D+", "");
		if (normalizedPhone.isBlank()) {
			throw new IllegalArgumentException("O chamado do WhatsApp não possui um telefone válido para resposta.");
		}
		return normalizedPhone;
	}

	private String resolveWhatsappRecipient(User requester) {
		if (requester != null && requester.getWhatsappTransportId() != null && !requester.getWhatsappTransportId().isBlank()) {
			return requester.getWhatsappTransportId();
		}
		return normalizePhone(requester == null ? null : requester.getPhoneNumber());
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

	private List<String> normalizeStatusCodes(String status) {
		if (status == null || status.isBlank()) {
			return List.of();
		}

		return Arrays.stream(status.split(","))
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.map(value -> value.toUpperCase(Locale.ROOT))
			.distinct()
			.toList();
	}

	private record DisplayStatus(String code, String name) {
	}

	public record AttachmentDownload(
		Resource resource,
		String originalFileName,
		String contentType,
		long sizeBytes
	) {
	}

	public record CreateWhatsappTicketRequest(
		User requester,
		String phoneNumber,
		String whatsappTransportId,
		UUID companyOwnerId,
		UUID sectorId,
		UUID assignedToUserId,
		String description,
		List<IncomingAttachment> attachments
	) {
	}

	public record IncomingAttachment(
		String originalFileName,
		String contentType,
		byte[] content
	) {
	}
}
