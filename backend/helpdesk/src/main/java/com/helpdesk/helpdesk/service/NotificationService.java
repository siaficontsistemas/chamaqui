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
import com.helpdesk.helpdesk.domain.TicketAssignmentNotification;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.notification.CalendarReminderNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyPartnershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TeamMembershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketAssignmentNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketTransferNotificationResponse;
import com.helpdesk.helpdesk.dto.ticket.RespondTicketTransferRequest;
import com.helpdesk.helpdesk.repository.CalendarObligationRepository;
import com.helpdesk.helpdesk.repository.CalendarReminderNotificationRepository;
import com.helpdesk.helpdesk.repository.CompanyPartnershipNotificationRepository;
import com.helpdesk.helpdesk.repository.TeamMembershipNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketAssignmentNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.TicketTransferNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class NotificationService {

	private final TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository;
	private final TicketTransferNotificationRepository ticketTransferNotificationRepository;
	private final TeamMembershipNotificationRepository teamMembershipNotificationRepository;
	private final CalendarReminderNotificationRepository calendarReminderNotificationRepository;
	private final CompanyPartnershipNotificationRepository companyPartnershipNotificationRepository;
	private final CalendarObligationRepository calendarObligationRepository;
	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;

	public NotificationService(
		TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository,
		TicketTransferNotificationRepository ticketTransferNotificationRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository,
		CalendarReminderNotificationRepository calendarReminderNotificationRepository,
		CompanyPartnershipNotificationRepository companyPartnershipNotificationRepository,
		CalendarObligationRepository calendarObligationRepository,
		TicketRepository ticketRepository,
		UserRepository userRepository
	) {
		this.ticketAssignmentNotificationRepository = ticketAssignmentNotificationRepository;
		this.ticketTransferNotificationRepository = ticketTransferNotificationRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
		this.calendarReminderNotificationRepository = calendarReminderNotificationRepository;
		this.companyPartnershipNotificationRepository = companyPartnershipNotificationRepository;
		this.calendarObligationRepository = calendarObligationRepository;
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<TicketAssignmentNotificationResponse> listTicketAssignments(String email) {
		return ticketAssignmentNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(email))
			.stream()
			.map(this::toTicketAssignmentResponse)
			.toList();
	}

	@Transactional
	public void deleteTicketAssignment(UUID notificationId, String email) {
		TicketAssignmentNotification notification = ticketAssignmentNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação não encontrada."));
		String normalizedEmail = normalizeEmail(email);

		if (!notification.getRecipient().getEmail().equalsIgnoreCase(normalizedEmail)) {
			throw new IllegalArgumentException("Essa notificação não pertence ao usuário informado.");
		}

		notification.setHidden(true);
		ticketAssignmentNotificationRepository.save(notification);
	}

	@Transactional(readOnly = true)
	public List<TicketTransferNotificationResponse> listTicketTransfers(String email) {
		return ticketTransferNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(email))
			.stream()
			.map(this::toTicketTransferResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamMembershipNotificationResponse> listTeamMemberships(String email) {
		return teamMembershipNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(email))
			.stream()
			.map(this::toTeamMembershipResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<CompanyPartnershipNotificationResponse> listCompanyPartnerships(String email) {
		return companyPartnershipNotificationRepository
			.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizeEmail(email))
			.stream()
			.map(this::toCompanyPartnershipResponse)
			.toList();
	}

	@Transactional
	public List<CalendarReminderNotificationResponse> listCalendarReminders(String email) {
		String normalizedEmail = normalizeEmail(email);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));
		OffsetDateTime now = OffsetDateTime.now();

		createMissingCalendarReminderNotifications(user, now);

		return calendarReminderNotificationRepository.findVisibleByRecipientEmailOrderByCreatedAtDesc(normalizedEmail)
			.stream()
			.filter(notification -> shouldShowCalendarReminder(notification.getObligation(), now))
			.map(notification -> toCalendarReminderResponse(notification, now))
			.toList();
	}

	@Transactional
	public void acceptTicketTransfer(UUID notificationId, RespondTicketTransferRequest request) {
		TicketTransferNotification notification = loadTransferNotification(notificationId);
		User recipient = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
			.orElseThrow(() -> new NotFoundException("Destinatário da transferência não encontrado."));

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
		User recipient = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
			.orElseThrow(() -> new NotFoundException("Destinatário da transferência não encontrado."));

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
		String normalizedEmail = normalizeEmail(email);

		if (!notification.getRecipient().getEmail().equalsIgnoreCase(normalizedEmail)) {
			throw new IllegalArgumentException("Essa notificação não pertence ao usuário informado.");
		}

		if (notification.getStatus() == TicketTransferStatus.PENDING) {
			throw new IllegalArgumentException("Responda a transferência antes de excluir essa notificação.");
		}

		notification.setHidden(true);
		ticketTransferNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteTeamMembership(UUID notificationId, String email) {
		TeamMembershipNotification notification = teamMembershipNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de remoção não encontrada."));
		String normalizedEmail = normalizeEmail(email);

		if (!notification.getRecipient().getEmail().equalsIgnoreCase(normalizedEmail)) {
			throw new IllegalArgumentException("Essa notificação não pertence ao usuário informado.");
		}

		notification.setHidden(true);
		teamMembershipNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteCalendarReminder(UUID notificationId, String email) {
		CalendarReminderNotification notification = calendarReminderNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de lembrete não encontrada."));
		String normalizedEmail = normalizeEmail(email);

		if (!notification.getRecipient().getEmail().equalsIgnoreCase(normalizedEmail)) {
			throw new IllegalArgumentException("Essa notificação não pertence ao usuário informado.");
		}

		notification.setHidden(true);
		calendarReminderNotificationRepository.save(notification);
	}

	@Transactional
	public void deleteCompanyPartnership(UUID notificationId, String email) {
		CompanyPartnershipNotification notification = companyPartnershipNotificationRepository.findDetailedById(notificationId)
			.orElseThrow(() -> new NotFoundException("Notificação de parceria não encontrada."));
		String normalizedEmail = normalizeEmail(email);

		if (!notification.getRecipient().getEmail().equalsIgnoreCase(normalizedEmail)) {
			throw new IllegalArgumentException("Essa notificação não pertence ao usuário informado.");
		}

		notification.setHidden(true);
		companyPartnershipNotificationRepository.save(notification);
	}

	private TicketAssignmentNotificationResponse toTicketAssignmentResponse(TicketAssignmentNotification notification) {
		return new TicketAssignmentNotificationResponse(
			notification.getId(),
			notification.getTicket().getId(),
			notification.getTicket().getProtocol(),
			notification.getTicket().getTitle(),
			notification.getTicket().getRequester().getFullName(),
			notification.getTicket().getSector().getName(),
			"ASSIGNED",
			notification.getCreatedAt()
		);
	}

	private TicketTransferNotificationResponse toTicketTransferResponse(TicketTransferNotification notification) {
		return new TicketTransferNotificationResponse(
			notification.getId(),
			notification.getTicket().getId(),
			notification.getTicket().getProtocol(),
			notification.getTicket().getTitle(),
			notification.getTicket().getRequester().getFullName(),
			notification.getTicket().getSector().getName(),
			notification.getSender().getFullName(),
			notification.getRecipient().getFullName(),
			notification.getStatus().name(),
			notification.getCreatedAt(),
			notification.getUpdatedAt(),
			notification.getRespondedAt()
		);
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

		return new TeamMembershipNotificationResponse(
			notification.getId(),
			notification.getType().name(),
			notification.getSector() == null ? null : notification.getSector().getName(),
			companyName,
			notification.getRemovedBy().getFullName(),
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

	private void createMissingCalendarReminderNotifications(User user, OffsetDateTime now) {
		List<CalendarObligation> obligations = user.getRoles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getCode()))
			? calendarObligationRepository.findVisibleByCompanyOwnerIdOrderByDueAtAsc(user.getId())
			: calendarObligationRepository.findVisibleByRecipientIdOrderByDueAtAsc(user.getId());

		for (CalendarObligation obligation : obligations) {
			if (!shouldShowCalendarReminder(obligation, now)) {
				continue;
			}

			for (User recipient : obligation.getRecipients()) {
				if (recipient == null) {
					continue;
				}

				if (calendarReminderNotificationRepository
					.existsByObligationIdAndRecipientId(obligation.getId(), recipient.getId())) {
					continue;
				}

				CalendarReminderNotification notification = new CalendarReminderNotification();
				notification.setObligation(obligation);
				notification.setRecipient(recipient);
				calendarReminderNotificationRepository.save(notification);
			}
		}
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
}
