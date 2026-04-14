package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.SectorMember;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketAssignmentNotification;
import com.helpdesk.helpdesk.domain.TicketAttachment;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.TicketPriority;
import com.helpdesk.helpdesk.domain.TicketStatus;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.ticket.CloseTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketMessageRequest;
import com.helpdesk.helpdesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.helpdesk.dto.ticket.RequestTicketTransferRequest;
import com.helpdesk.helpdesk.dto.ticket.TicketAttachmentResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketMessageResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketSummaryResponse;
import com.helpdesk.helpdesk.dto.ticket.TicketTransferCandidateResponse;
import com.helpdesk.helpdesk.repository.SectorMemberRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TicketAssignmentNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketAttachmentRepository;
import com.helpdesk.helpdesk.repository.TicketMessageRepository;
import com.helpdesk.helpdesk.repository.TicketPriorityRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.TicketStatusRepository;
import com.helpdesk.helpdesk.repository.TicketTransferNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class TicketService {

	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;
	private final SectorMemberRepository sectorMemberRepository;
	private final SectorRepository sectorRepository;
	private final TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository;
	private final TicketStatusRepository ticketStatusRepository;
	private final TicketPriorityRepository ticketPriorityRepository;
	private final TicketMessageRepository ticketMessageRepository;
	private final TicketAttachmentRepository ticketAttachmentRepository;
	private final TicketTransferNotificationRepository ticketTransferNotificationRepository;
	private final TicketAttachmentStorageService ticketAttachmentStorageService;
	private final TicketClosureEmailService ticketClosureEmailService;

	public TicketService(
		TicketRepository ticketRepository,
		UserRepository userRepository,
		SectorMemberRepository sectorMemberRepository,
		SectorRepository sectorRepository,
		TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository,
		TicketStatusRepository ticketStatusRepository,
		TicketPriorityRepository ticketPriorityRepository,
		TicketMessageRepository ticketMessageRepository,
		TicketAttachmentRepository ticketAttachmentRepository,
		TicketTransferNotificationRepository ticketTransferNotificationRepository,
		TicketAttachmentStorageService ticketAttachmentStorageService,
		TicketClosureEmailService ticketClosureEmailService
	) {
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
		this.sectorMemberRepository = sectorMemberRepository;
		this.sectorRepository = sectorRepository;
		this.ticketAssignmentNotificationRepository = ticketAssignmentNotificationRepository;
		this.ticketStatusRepository = ticketStatusRepository;
		this.ticketPriorityRepository = ticketPriorityRepository;
		this.ticketMessageRepository = ticketMessageRepository;
		this.ticketAttachmentRepository = ticketAttachmentRepository;
		this.ticketTransferNotificationRepository = ticketTransferNotificationRepository;
		this.ticketAttachmentStorageService = ticketAttachmentStorageService;
		this.ticketClosureEmailService = ticketClosureEmailService;
	}

	@Transactional(readOnly = true)
	public List<TicketResponse> list(String email, String status) {
		String normalizedEmail = normalizeEmail(email);
		List<String> statusCodes = normalizeStatusCodes(status);
		List<Ticket> tickets = statusCodes.isEmpty()
			? ticketRepository.findVisibleByEmailOrderByCreatedAtDesc(normalizedEmail)
			: ticketRepository.findVisibleByEmailAndStatusCodesOrderByCreatedAtDesc(
				normalizedEmail,
				statusCodes
			);

		return tickets.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public TicketResponse get(UUID ticketId, String email) {
		return toResponse(loadDetailedAccessibleTicket(ticketId, email));
	}

	@Transactional(readOnly = true)
	public TicketSummaryResponse summary(String email) {
		List<Ticket> tickets = ticketRepository.findVisibleByEmailOrderByCreatedAtDesc(normalizeEmail(email));
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
		User author = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
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

	@Transactional
	public TicketResponse create(CreateTicketRequest request, List<MultipartFile> files) {
		User requester = userRepository.findByEmailIgnoreCase(normalizeEmail(request.requesterEmail()))
			.orElseThrow(() -> new NotFoundException("Solicitante não encontrado."));
		TicketStatus status = ticketStatusRepository.findByCode("OPEN")
			.orElseThrow(() -> new NotFoundException("Status padrão de abertura não encontrado."));
		TicketPriority priority = ticketPriorityRepository.findByCode(request.priorityCode().trim().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new NotFoundException("Prioridade não encontrada."));
		com.helpdesk.helpdesk.domain.Sector sector = sectorRepository.findById(request.sectorId())
			.orElseThrow(() -> new NotFoundException("Setor não encontrado."));

		if (!sector.getCreatedBy().getId().equals(request.companyOwnerId())) {
			throw new IllegalArgumentException("O setor informado não pertence a empresa selecionada.");
		}

		Ticket ticket = new Ticket();
		ticket.setProtocol(nextProtocol());
		ticket.setTitle(request.title().trim());
		ticket.setDescription(request.description().trim());
		ticket.setRequester(requester);
		ticket.setAssignedTo(resolveNextAssignee(sector));
		ticket.setSector(sector);
		ticket.setStatus(status);
		ticket.setPriority(priority);
		ticket.setCopyEmail(normalizeOptionalEmail(request.copyEmail()));

		Ticket savedTicket = ticketRepository.save(ticket);
		createAssignmentNotification(savedTicket);
		TicketMessage initialMessage = ensureInitialMessage(savedTicket);
		saveAttachments(savedTicket, initialMessage, requester, files);

		return toResponse(savedTicket);
	}

	@Transactional
	public TicketResponse requestTransfer(UUID ticketId, RequestTicketTransferRequest request) {
		User author = userRepository.findByEmailIgnoreCase(normalizeEmail(request.authorEmail()))
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
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, email);
		ensureInitialMessage(ticket);
		Map<UUID, List<TicketAttachmentResponse>> attachmentsByMessageId = loadAttachmentsByMessageId(ticketId);

		return ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
			.map(message -> toMessageResponse(message, attachmentsByMessageId))
			.toList();
	}

	@Transactional
	public TicketMessageResponse addMessage(UUID ticketId, CreateTicketMessageRequest request, List<MultipartFile> files) {
		User author = userRepository.findByEmailIgnoreCase(normalizeEmail(request.authorEmail()))
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

		if (ticket.getFirstResponseAt() == null && !author.getId().equals(ticket.getRequester().getId())) {
			ticket.setFirstResponseAt(savedMessage.getCreatedAt());
		}

		if (!author.getId().equals(ticket.getRequester().getId()) && !"CLOSED".equalsIgnoreCase(ticket.getStatus().getCode())) {
			TicketStatus inProgressStatus = ticketStatusRepository.findByCode("IN_PROGRESS")
				.orElseThrow(() -> new NotFoundException("Status em andamento não encontrado."));
			ticket.setStatus(inProgressStatus);
		}

		ticketRepository.save(ticket);

		return toMessageResponse(savedMessage, attachments);
	}

	@Transactional
	public TicketResponse closeTicket(UUID ticketId, CloseTicketRequest request) {
		User author = userRepository.findByEmailIgnoreCase(normalizeEmail(request.authorEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pelo fechamento não encontrado."));
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, author.getEmail());

		if (!hasRole(author, "admin") && !hasRole(author, "employee")) {
			throw new IllegalArgumentException("Apenas administradores e funcionários podem fechar chamados.");
		}

		if ("CLOSED".equalsIgnoreCase(ticket.getStatus().getCode())) {
			return toResponse(ticket);
		}

		TicketStatus closedStatus = ticketStatusRepository.findByCode("CLOSED")
			.orElseThrow(() -> new NotFoundException("Status de fechamento não encontrado."));
		OffsetDateTime closedAt = OffsetDateTime.now();

		ticket.setStatus(closedStatus);
		ticket.setResolvedAt(ticket.getResolvedAt() == null ? closedAt : ticket.getResolvedAt());
		ticket.setClosedAt(closedAt);

		Ticket savedTicket = ticketRepository.save(ticket);
		ticketClosureEmailService.sendConversationTranscript(
			savedTicket,
			ticketMessageRepository.findByTicketIdOrderByCreatedAtAsc(savedTicket.getId()),
			loadAttachmentEntitiesByMessageId(savedTicket.getId())
		);

		return toResponse(savedTicket);
	}

	@Transactional(readOnly = true)
	public AttachmentDownload downloadAttachment(UUID ticketId, UUID attachmentId, String email) {
		Ticket ticket = loadDetailedAccessibleTicket(ticketId, email);
		TicketAttachment attachment = ticketAttachmentRepository.findByIdAndTicketId(attachmentId, ticket.getId())
			.orElseThrow(() -> new NotFoundException("Anexo não encontrado para este chamado."));
		Resource resource = ticketAttachmentStorageService.loadAsResource(attachment.getStorageKey());

		return new AttachmentDownload(
			resource,
			attachment.getOriginalFileName(),
			attachment.getContentType(),
			attachment.getSizeBytes()
		);
	}

	private String nextProtocol() {
		long nextNumber = ticketRepository.count() + 1;
		return "HD-2026-" + String.format("%04d", nextNumber);
	}

	private TicketResponse toResponse(Ticket ticket) {
		DisplayStatus displayStatus = resolveDisplayStatus(ticket);
		return new TicketResponse(
			ticket.getId(),
			ticket.getProtocol(),
			ticket.getTitle(),
			ticket.getDescription(),
			ticket.getRequester().getFullName(),
			ticket.getRequester().getEmail(),
			ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getFullName(),
			ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getEmail(),
			ticket.getSector().getName(),
			displayStatus.code(),
			displayStatus.name(),
			ticket.getPriority().getCode(),
			ticket.getPriority().getName(),
			ticket.getOpenedAt(),
			ticket.getClosedAt(),
			ticket.getPendingTransferTo() == null ? null : ticket.getPendingTransferTo().getFullName()
		);
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

		boolean isRequesterLastAuthor = lastMessage.getAuthor().getId().equals(ticket.getRequester().getId());

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

		if (!hasRole(author, "employee")) {
			throw new IllegalArgumentException("Apenas funcionários podem transferir chamados.");
		}

		if (ticket.getAssignedTo() == null || !ticket.getAssignedTo().getId().equals(author.getId())) {
			throw new IllegalArgumentException("Somente o funcionário atualmente responsável pode transferir o chamado.");
		}

		if (ticket.getPendingTransferTo() != null
			|| ticketTransferNotificationRepository.existsByTicketIdAndStatus(ticket.getId(), TicketTransferStatus.PENDING)) {
			throw new IllegalArgumentException("Esse chamado já possui uma transferência pendente.");
		}
	}

	private Ticket loadDetailedAccessibleTicket(UUID ticketId, String email) {
		return ticketRepository.findDetailedVisibleByIdAndEmail(ticketId, normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Chamado não encontrado ou indisponível para esse usuário."));
	}

	private User resolveNextAssignee(com.helpdesk.helpdesk.domain.Sector sector) {
		List<SectorMember> eligibleMembers = sectorMemberRepository.findBySectorIdOrderByAssignedAtAsc(sector.getId()).stream()
			.filter(member -> member.getUser() != null)
			.filter(member -> member.getUser().getStatus() != null)
			.filter(member -> member.getUser().getStatus().name().equalsIgnoreCase("ACTIVE"))
			.filter(member -> hasRole(member.getUser(), "employee"))
			.toList();

		if (eligibleMembers.isEmpty()) {
			throw new IllegalArgumentException("Esse setor não possui funcionários disponíveis para receber chamados.");
		}

		List<UUID> eligibleUserIds = eligibleMembers.stream()
			.map(member -> member.getUser().getId())
			.toList();

		UUID lastAssignedUserId = ticketRepository
			.findFirstBySectorIdAndAssignedToIdInOrderByCreatedAtDesc(sector.getId(), eligibleUserIds)
			.map(Ticket::getAssignedTo)
			.map(User::getId)
			.orElse(null);

		if (lastAssignedUserId == null) {
			return eligibleMembers.get(0).getUser();
		}

		for (int index = 0; index < eligibleMembers.size(); index++) {
			if (!eligibleMembers.get(index).getUser().getId().equals(lastAssignedUserId)) {
				continue;
			}

			int nextIndex = (index + 1) % eligibleMembers.size();
			return eligibleMembers.get(nextIndex).getUser();
		}

		return eligibleMembers.get(0).getUser();
	}

	private void createAssignmentNotification(Ticket ticket) {
		if (ticket.getAssignedTo() == null) {
			return;
		}

		TicketAssignmentNotification notification = new TicketAssignmentNotification();
		notification.setTicket(ticket);
		notification.setRecipient(ticket.getAssignedTo());
		ticketAssignmentNotificationRepository.save(notification);
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

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream()
			.anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
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
}
