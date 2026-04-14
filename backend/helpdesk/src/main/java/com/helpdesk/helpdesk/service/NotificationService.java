package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.TeamMembershipNotification;
import com.helpdesk.helpdesk.domain.TicketAssignmentNotification;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.notification.TeamMembershipNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketAssignmentNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.TicketTransferNotificationResponse;
import com.helpdesk.helpdesk.dto.ticket.RespondTicketTransferRequest;
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
	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;

	public NotificationService(
		TicketAssignmentNotificationRepository ticketAssignmentNotificationRepository,
		TicketTransferNotificationRepository ticketTransferNotificationRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository,
		TicketRepository ticketRepository,
		UserRepository userRepository
	) {
		this.ticketAssignmentNotificationRepository = ticketAssignmentNotificationRepository;
		this.ticketTransferNotificationRepository = ticketTransferNotificationRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
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

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe o email do usuário.");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
