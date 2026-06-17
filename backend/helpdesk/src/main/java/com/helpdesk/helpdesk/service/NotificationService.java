package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CalendarObligation;
import com.helpdesk.helpdesk.domain.CalendarReminderNotification;
import com.helpdesk.helpdesk.domain.CompanyPartnershipNotification;
import com.helpdesk.helpdesk.domain.CompanyPartnershipNotificationType;
import com.helpdesk.helpdesk.domain.TeamMembershipNotification;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketAssignmentNotification;
import com.helpdesk.helpdesk.domain.TicketClosureNotification;
import com.helpdesk.helpdesk.domain.TicketMessage;
import com.helpdesk.helpdesk.domain.TicketReplyNotification;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.notification.CalendarReminderNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyPartnershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TeamMembershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketAssignmentNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketClosureNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketReplyNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketTransferNotificationResponse;
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

@Service
public class NotificationService {

	private final TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository;
	private final TicketTransferNotificationRepository ticketTransferNotificationRepository;
	private final TeamMembershipNotificationRepository teamMembershipNotificationRepository;
	private final CalendarReminderNotificationRepository calendarReminderNotificationRepository;
	private final CompanyPartnershipNotificationRepository companyPartnershipNotificationRepository;
	private final TicketClosureNotificationRepository ticketClosureNotificationRepository;
	private final TicketReplyNotificationRepository ticketReplyNotificationRepository;
	private final TicketMessageRepository ticketMessageRepository;
	private final CalendarObligationRepository calendarObligationRepository;
	private final TicketRepository ticketRepository;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;
	private final CalendarReminderWhatsappDispatchService calendarReminderWhatsappDispatchService;

	public NotificationService(
		TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository,
		TicketTransferNotificationRepository ticketTransferNotificationRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository,
		CalendarReminderNotificationRepository calendarReminderNotificationRepository,
		CompanyPartnershipNotificationRepository companyPartnershipNotificationRepository,
		TicketClosureNotificationRepository ticketClosureNotificationRepository,
		TicketReplyNotificationRepository ticketReplyNotificationRepository,
		TicketMessageRepository ticketMessageRepository,
		CalendarObligationRepository calendarObligationRepository,
		TicketRepository ticketRepository,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService,
		CalendarReminderWhatsappDispatchService calendarReminderWhatsappDispatchService
	) {
		this.ticketAssignmentNotificationRepository = ticketAssignmentNotificationRepository;
		this.ticketTransferNotificationRepository = ticketTransferNotificationRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
		this.calendarReminderNotificationRepository = calendarReminderNotificationRepository;
		this.companyPartnershipNotificationRepository = companyPartnershipNotificationRepository;
		this.ticketClosureNotificationRepository = ticketClosureNotificationRepository;
		this.ticketReplyNotificationRepository = ticketReplyNotificationRepository;
		this.ticketMessageRepository = ticketMessageRepository;
		this.calendarObligationRepository = calendarObligationRepository;
		this.ticketRepository = ticketRepository;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
		this.calendarReminderWhatsappDispatchService = calendarReminderWhatsappDispatchService;
	}

	@Transactional(readOnly = true)
	public List<TicketAssignmentNotificationResponse> listTicketAssignments(String email) {
		User viewer = loadUserByEmail(email);
		return ticketAssignmentNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(viewer.getEmail()))
			.stream()
			.map(this::toTicketAssignmentResponse)
			.toList();
	}

	@Transactional
	public void deleteTicketAssignment(UUID notificationId, String email) {
		TicketAssignmentNotification notification = ticketAssignmentNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação não encontrada."));
		ensureNotificationRecipient(notification.getRecipient(), loadUserByEmail(email));

		notification.setHidden(true);
		ticketAssignmentNotificationRepository.save(notification);
	}

	@Transactional(readOnly = true)
	public List<TicketTransferNotificationResponse> listTicketTransfers(String email) {
		User viewer = loadUserByEmail(email);
		return ticketTransferNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(viewer.getEmail()))
			.stream()
			.map(this::toTicketTransferResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TicketClosureNotificationResponse> listTicketClosures(String email) {
		User viewer = loadUserByEmail(email);
		return ticketClosureNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(viewer.getEmail()))
			.stream()
			.map(this::toTicketClosureResponse)
			.toList();
	}

	@Transactional
	public List<TicketReplyNotificationResponse> listTicketReplies(String email) {
		User viewer = loadUserByEmail(email);
		ensureMissingTicketReplyNotifications(viewer);
		return ticketReplyNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(viewer.getEmail()))
			.stream()
			.map(this::toTicketReplyResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamMembershipNotificationResponse> listTeamMemberships(String email) {
		User viewer = loadUserByEmail(email);
		return teamMembershipNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(viewer.getEmail()))
			.stream()
			.map(this::toTeamMembershipResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<CompanyPartnershipNotificationResponse> listCompanyPartnerships(String email) {
		User viewer = loadUserByEmail(email);
		return companyPartnershipNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(viewer.getEmail()))
			.stream()
			.map(this::toCompanyPartnershipResponse)
			.toList();
	}

	@Transactional
	public List<CalendarReminderNotificationResponse> listCalendarReminders(String email) {
		User user = loadUserByEmail(email);
		String normalizedEmail = normalizeEmail(user.getEmail());
		OffsetDateTime now = OffsetDateTime.now();

		createMissingCalendarReminderNotifications(user, now);

		return calendarReminderNotificationRepository.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizedEmail)
			.stream()
			.filter(notification -> shouldShowCalendarReminder(notification.getObligation(), now))
			.map(notification -> toCalendarReminderResponse(notification, now))
			.toList();
	}

	@Transactional
	public int createMissingCalendarReminderNotificationsForCompanyOwner(UUID companyOwnerId, OffsetDateTime now) {
		return calendarReminderWhatsappDispatchService.createMissingCalendarReminderNotificationsForCompanyOwner(companyOwnerId, now);
	}

	@Transactional
	public void acceptTicketTransfer(UUID notificationId, RespondTicketTransferRequest request) {
		TicketTransferNotification notification = loadTransferNotification(notificationId);
		User recipient = loadUserByEmail(request.email());

		ensureTransferRecipient(notification, recipient);

		if (notification.getStatus() != TicketTransferStatus.PENDING) {
			throw new IllegalArgumentException("Essa transferência já foi respondida.");
		}

		if (notification.getTicket().getPendingTransferTo() == null
			|| !notification.getTicket().getPendingTransferTo().getId().equals(recipient.getId())) {
			throw new IllegalArgumentException("Essa transferência não está mais pendente para o usuário informado.");
		}

		notification.getTicket().setAssignedTo(recipient);
		clearPendingTransfer(notification);
		notification.getTicket().setResolvedAt(notification.getTicket().getResolvedAt());
		ticketRepository.save(notification.getTicket());

		notification.setStatus(TicketTransferStatus.ACCEPTED);
		notification.setRespondedAt(OffsetDateTime.now());
		ticketTransferNotificationRepository.save(notification);
	}

	@Transactional
	public void declineTicketTransfer(UUID notificationId, RespondTicketTransferRequest request) {
		TicketTransferNotification notification = loadTransferNotification(notificationId);
		User recipient = loadUserByEmail(request.email());

		ensureTransferRecipient(notification, recipient);

		if (notification.getStatus() != TicketTransferStatus.PENDING) {
			throw new IllegalArgumentException("Essa transferência já foi respondida.");
		}

		clearPendingTransfer(notification);
		ticketRepository.save(notification.getTicket());

		notification.setStatus(TicketTransferStatus.DECLINED);
		notification.setRespondedAt(OffsetDateTime.now());
		ticketTransferNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteTicketTransfer(UUID notificationId, String email) {
		TicketTransferNotification notification = loadTransferNotification(notificationId);
		ensureNotificationRecipient(notification.getRecipient(), loadUserByEmail(email));

		if (notification.getStatus() == TicketTransferStatus.PENDING) {
			throw new IllegalArgumentException("Responda a transferência antes de excluir essa notificação.");
		}

		notification.setHidden(true);
		ticketTransferNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteTicketClosure(UUID notificationId, String email) {
		TicketClosureNotification notification = ticketClosureNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de fechamento não encontrada."));
		ensureNotificationRecipient(notification.getRecipient(), loadUserByEmail(email));

		notification.setHidden(true);
		ticketClosureNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteTicketReply(UUID notificationId, String email) {
		TicketReplyNotification notification = ticketReplyNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de resposta não encontrada."));
		ensureNotificationRecipient(notification.getRecipient(), loadUserByEmail(email));

		notification.setHidden(true);
		ticketReplyNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteTeamMembership(UUID notificationId, String email) {
		TeamMembershipNotification notification = teamMembershipNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de remoção não encontrada."));
		ensureNotificationRecipient(notification.getRecipient(), loadUserByEmail(email));

		notification.setHidden(true);
		teamMembershipNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteCalendarReminder(UUID notificationId, String email) {
		CalendarReminderNotification notification = calendarReminderNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de lembrete não encontrada."));
		ensureNotificationRecipient(notification.getRecipient(), loadUserByEmail(email));

		notification.setHidden(true);
		calendarReminderNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteCompanyPartnership(UUID notificationId, String email) {
		CompanyPartnershipNotification notification = companyPartnershipNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de parceria não encontrada."));
		ensureNotificationRecipient(notification.getRecipient(), loadUserByEmail(email));

		notification.setHidden(true);
		companyPartnershipNotificationRepository.save(notification);
	}

	private TicketAssignmentNotificationResponse toTicketAssignmentResponse(TicketAssignmentNotification notification) {
		String companyName = resolveTicketCompanyName(notification.getTicket());
		String requesterCompanyName = resolveRequesterCompanyName(notification.getTicket());
		return new TicketAssignmentNotificationResponse(
			notification.getId(),
			notification.getTicket().getId(),
			notification.getTicket().getProtocol(),
			notification.getTicket().getTitle(),
			notification.getTicket().getRequester().getFullName(),
			notification.getTicket().getSector().getName(),
			companyName,
			requesterCompanyName,
			"ASSIGNED",
			notification.getCreatedAt()
		);
	}

	private TicketTransferNotificationResponse toTicketTransferResponse(TicketTransferNotification notification) {
		String companyName = resolveTicketCompanyName(notification.getTicket());
		String requesterCompanyName = resolveRequesterCompanyName(notification.getTicket());
		return new TicketTransferNotificationResponse(
			notification.getId(),
			notification.getTicket().getId(),
			notification.getTicket().getProtocol(),
			notification.getTicket().getTitle(),
			notification.getTicket().getRequester().getFullName(),
			notification.getTicket().getSector().getName(),
			companyName,
			requesterCompanyName,
			notification.getSender().getFullName(),
			notification.getRecipient().getFullName(),
			notification.getStatus().name(),
			notification.getCreatedAt(),
			notification.getUpdatedAt(),
			notification.getRespondedAt()
		);
	}

	private TicketClosureNotificationResponse toTicketClosureResponse(TicketClosureNotification notification) {
		return new TicketClosureNotificationResponse(
			notification.getId(),
			notification.getTicket().getId(),
			notification.getTicket().getProtocol(),
			notification.getTicket().getTitle(),
			notification.getTicket().getSector().getName(),
			resolveTicketCompanyName(notification.getTicket()),
			notification.getClosedBy().getFullName(),
			notification.getCreatedAt()
		);
	}

	private TicketReplyNotificationResponse toTicketReplyResponse(TicketReplyNotification notification) {
		String companyName = resolveTicketCompanyName(notification.getTicket());
		String requesterCompanyName = resolveRequesterCompanyName(notification.getTicket());
		String messagePreview = notification.getMessage() == null || notification.getMessage().getMessage() == null
			? ""
			: notification.getMessage().getMessage().trim();
		if (messagePreview.length() > 140) {
			messagePreview = messagePreview.substring(0, 140).trim() + "...";
		}

		return new TicketReplyNotificationResponse(
			notification.getId(),
			notification.getTicket().getId(),
			notification.getTicket().getProtocol(),
			notification.getTicket().getTitle(),
			notification.getTicket().getRequester().getFullName(),
			notification.getTicket().getSector().getName(),
			companyName,
			requesterCompanyName,
			messagePreview,
			"NEW_REPLY",
			notification.getCreatedAt()
		);
	}

	private String resolveTicketCompanyName(com.helpdesk.helpdesk.domain.Ticket ticket) {
		if (ticket == null || ticket.getSector() == null || ticket.getSector().getCreatedBy() == null) {
			return "Empresa não informada";
		}

		String companyName = ticket.getSector().getCreatedBy().getCompanyName();
		if (companyName == null || companyName.isBlank()) {
			companyName = ticket.getSector().getCreatedBy().getFullName();
		}

		return companyName == null || companyName.isBlank() ? "Empresa não informada" : companyName;
	}

	private String resolveRequesterCompanyName(com.helpdesk.helpdesk.domain.Ticket ticket) {
		if (ticket == null || ticket.getRequester() == null) {
			return "";
		}

		User requester = ticket.getRequester();
		User requesterCompany = requester.getCompanyOwner() != null ? requester.getCompanyOwner() : requester;
		String companyName = requesterCompany.getCompanyName();

		if ((companyName == null || companyName.isBlank())
			&& requesterCompany.getCompanyDocument() != null
			&& !requesterCompany.getCompanyDocument().isBlank()) {
			companyName = requesterCompany.getFullName();
		}

		return companyName == null ? "" : companyName.trim();
	}

	private TeamMembershipNotificationResponse toTeamMembershipResponse(TeamMembershipNotification notification) {
		String companyName = notification.getCompanyName();
		if ((companyName == null || companyName.isBlank()) && notification.getSector() != null) {
			companyName = notification.getSector().getCreatedBy().getCompanyName();
		}
		if ((companyName == null || companyName.isBlank()) && notification.getRemovedBy() != null) {
			companyName = notification.getRemovedBy().getCompanyName();
		}
		if ((companyName == null || companyName.isBlank()) && notification.getRemovedBy() != null) {
			companyName = notification.getRemovedBy().getFullName();
		}

		String removedByName = notification.getRemovedBy().getFullName();
		if (notification.getType() == com.helpdesk.helpdesk.domain.TeamMembershipNotificationType.COMPANY_DELETED
			&& notification.getRecipient() != null
			&& notification.getRemovedBy() != null
			&& notification.getRecipient().getId().equals(notification.getRemovedBy().getId())) {
			removedByName = "Sistema";
		}

		return new TeamMembershipNotificationResponse(
			notification.getId(),
			notification.getType().name(),
			notification.getSector() == null ? null : notification.getSector().getName(),
			companyName,
			removedByName,
			notification.getCreatedAt()
		);
	}

	private CalendarReminderNotificationResponse toCalendarReminderResponse(
		CalendarReminderNotification notification,
		OffsetDateTime now
	) {
		CalendarObligation obligation = notification.getObligation();
		String companyName = obligation.getCompanyOwner().getCompanyName();

		if (companyName == null || companyName.isBlank()) {
			companyName = obligation.getCompanyOwner().getFullName();
		}

		return new CalendarReminderNotificationResponse(
			notification.getId(),
			obligation.getId(),
			obligation.getTitle(),
			obligation.getDescription(),
			obligation.getDueAt(),
			obligation.getReminderAt(),
			obligation.getCreatedBy().getFullName(),
			companyName,
			resolveCalendarObligationStatus(obligation, now),
			notification.getCreatedAt()
		);
	}

	private CompanyPartnershipNotificationResponse toCompanyPartnershipResponse(
		CompanyPartnershipNotification notification
	) {
		boolean canRespond = notification.getType() == CompanyPartnershipNotificationType.REQUESTED
			&& notification.getRecipient().getId().equals(notification.getTargetCompanyId());

		String status = switch (notification.getType()) {
			case REQUESTED -> "PENDING";
			case ACCEPTED -> "ACCEPTED";
			case UNLINKED -> "REMOVED";
		};

		return new CompanyPartnershipNotificationResponse(
			notification.getId(),
			notification.getCompanyPartnershipId(),
			notification.getType().name(),
			notification.getActorUser().getFullName(),
			notification.getActorUser().getCompanyName(),
			notification.getRequesterCompanyId(),
			notification.getRequesterCompanyName(),
			notification.getTargetCompanyId(),
			notification.getTargetCompanyName(),
			status,
			canRespond,
			notification.getCreatedAt()
		);
	}

	private TicketTransferNotification loadTransferNotification(UUID notificationId) {
		return ticketTransferNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de transferência não encontrada."));
	}

	private void ensureTransferRecipient(TicketTransferNotification notification, User recipient) {
		if (!notification.getRecipient().getId().equals(recipient.getId())) {
			throw new IllegalArgumentException("Essa transferência não pertence ao usuário informado.");
		}
	}

	private void clearPendingTransfer(TicketTransferNotification notification) {
		notification.getTicket().setPendingTransferTo(null);
		notification.getTicket().setPendingTransferRequestedBy(null);
		notification.getTicket().setPendingTransferRequestedAt(null);
	}

	private void ensureMissingTicketReplyNotifications(User viewer) {
		for (Ticket ticket : ticketRepository.findVisibleByEmailOrderByCreatedAtDesc(normalizeEmail(viewer.getEmail()))) {
			if (!shouldReceiveTicketReplyNotification(ticket, viewer)) {
				continue;
			}

			ticketMessageRepository.findFirstByTicketIdOrderByCreatedAtDesc(ticket.getId())
				.filter(message -> shouldCreateTicketReplyNotification(ticket, message))
				.ifPresent(message -> ensureTicketReplyNotification(ticket, message, viewer));
		}
	}

	private boolean shouldReceiveTicketReplyNotification(Ticket ticket, User viewer) {
		if (ticket == null || viewer == null || ticket.getRequester() == null || ticket.getStatus() == null) {
			return false;
		}

		if (ticket.getDeletedAt() != null || "CLOSED".equalsIgnoreCase(ticket.getStatus().getCode())) {
			return false;
		}

		if (ticket.getRequester().getId().equals(viewer.getId())) {
			return false;
		}

		if (hasRole(viewer, "ADMIN")) {
			return ticket.getSector() != null
				&& ticket.getSector().getCreatedBy() != null
				&& ticket.getSector().getCreatedBy().getId().equals(viewer.getId());
		}

		if (hasRole(viewer, "EMPLOYEE")) {
			return ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(viewer.getId());
		}

		return false;
	}

	private boolean shouldCreateTicketReplyNotification(Ticket ticket, TicketMessage message) {
		if (ticket == null || message == null || message.getAuthor() == null || ticket.getRequester() == null) {
			return false;
		}

		return isRequesterSideAuthor(ticket, message.getAuthor());
	}

	private boolean isRequesterSideAuthor(Ticket ticket, User author) {
		if (ticket == null || author == null || ticket.getRequester() == null) {
			return false;
		}

		if (isResponderSideAuthor(ticket, author)) {
			return false;
		}

		if (ticket.getRequester().getId().equals(author.getId())) {
			return true;
		}

		User requesterCompany = ticket.getRequester().getCompanyOwner();
		if (requesterCompany == null) {
			return false;
		}

		if (requesterCompany.getId().equals(author.getId())) {
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
		if (responderCompany.getId().equals(author.getId())) {
			return true;
		}

		User authorCompany = author.getCompanyOwner();
		return authorCompany != null && responderCompany.getId().equals(authorCompany.getId());
	}

	private void ensureTicketReplyNotification(Ticket ticket, TicketMessage message, User recipient) {
		TicketReplyNotification matchingNotification = ticketReplyNotificationRepository
			.findByMessageIdAndRecipientId(message.getId(), recipient.getId())
			.orElse(null);
		List<TicketReplyNotification> ticketNotifications = ticketReplyNotificationRepository
			.findByTicketIdAndRecipientId(ticket.getId(), recipient.getId());
		boolean hasChanges = false;

		for (TicketReplyNotification notification : ticketNotifications) {
			boolean isMatchingNotification = matchingNotification != null
				&& notification.getId().equals(matchingNotification.getId());

			if (isMatchingNotification) {
				if (notification.isHidden()) {
					notification.setHidden(false);
					hasChanges = true;
				}
				continue;
			}

			if (!notification.isHidden()) {
				notification.setHidden(true);
				hasChanges = true;
			}
		}

		if (matchingNotification == null) {
			TicketReplyNotification notification = new TicketReplyNotification();
			notification.setTicket(ticket);
			notification.setMessage(message);
			notification.setRecipient(recipient);
			ticketReplyNotificationRepository.save(notification);
		} else if (hasChanges) {
			ticketReplyNotificationRepository.saveAll(ticketNotifications);
		}
	}

	private void createMissingCalendarReminderNotifications(User user, OffsetDateTime now) {
		List<CalendarObligation> obligations = user.getRoles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getCode()))
			? calendarObligationRepository.findVisibleByCompanyOwnerIdOrderByDueAtAsc(user.getId())
			: calendarObligationRepository.findVisibleByRecipientIdOrderByDueAtAsc(user.getId());

		createMissingCalendarReminderNotifications(obligations, now);
	}

	private int createMissingCalendarReminderNotifications(List<CalendarObligation> obligations, OffsetDateTime now) {
		int createdCount = 0;

		for (CalendarObligation obligation : obligations) {
			if (!shouldShowCalendarReminder(obligation, now)) {
				continue;
			}

			for (User recipient : obligation.getRecipients()) {
				if (recipient == null) {
					continue;
				}

				if (ensureCalendarReminderNotification(obligation, recipient)) {
					createdCount++;
				}

				calendarReminderWhatsappDispatchService.dispatchReminderIfNeeded(
					resolveCompanyOwnerForWhatsappDispatch(obligation),
					obligation,
					recipient,
					now
				);
			}
		}

		return createdCount;
	}

	private boolean ensureCalendarReminderNotification(CalendarObligation obligation, User recipient) {
		if (calendarReminderNotificationRepository.findByObligationIdAndRecipientId(obligation.getId(), recipient.getId()).isPresent()) {
			return false;
		}

		CalendarReminderNotification notification = new CalendarReminderNotification();
		notification.setObligation(obligation);
		notification.setRecipient(recipient);
		calendarReminderNotificationRepository.save(notification);
		return true;
	}

	private boolean shouldShowCalendarReminder(CalendarObligation obligation, OffsetDateTime now) {
		if (obligation.getCompletedAt() != null) {
			return false;
		}

		if (obligation.getReminderAt() != null && !obligation.getReminderAt().isAfter(now)) {
			return true;
		}

		if (obligation.getDueAt().isBefore(now)) {
			return true;
		}

		return obligation.getDueAt().toLocalDate().isEqual(now.toLocalDate());
	}

	private User resolveCompanyOwnerForWhatsappDispatch(CalendarObligation obligation) {
		if (obligation == null || obligation.getCompanyOwner() == null || obligation.getCompanyOwner().getId() == null) {
			return null;
		}

		return calendarObligationRepository.findDetailedById(obligation.getId())
			.map(CalendarObligation::getCompanyOwner)
			.flatMap(companyOwner -> scopedUserLookupService.findUniqueByEmailInCurrentTenant(companyOwner.getEmail()))
			.orElse(obligation.getCompanyOwner());
	}

	private String resolveCalendarObligationStatus(CalendarObligation obligation, OffsetDateTime now) {
		if (obligation.getCompletedAt() != null) {
			return "COMPLETED";
		}

		if (obligation.getDueAt().isBefore(now)) {
			return "OVERDUE";
		}

		if (obligation.getDueAt().toLocalDate().isEqual(now.toLocalDate())) {
			return "DUE_TODAY";
		}

		return "UPCOMING";
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe o email do usuário.");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream().anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private User loadUserByEmail(String email) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");
		return user;
	}

	private void ensureNotificationRecipient(User notificationRecipient, User currentUser) {
		if (notificationRecipient == null || currentUser == null || !notificationRecipient.getId().equals(currentUser.getId())) {
			throw new IllegalArgumentException("Essa notificação não pertence ao usuário informado.");
		}
	}
}
