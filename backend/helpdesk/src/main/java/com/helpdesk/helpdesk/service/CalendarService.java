package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CalendarObligation;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.calendar.CalendarObligationResponse;
import com.helpdesk.helpdesk.dto.calendar.CreateCalendarObligationRequest;
import com.helpdesk.helpdesk.dto.calendar.UpdateCalendarObligationRequest;
import com.helpdesk.helpdesk.repository.CalendarObligationRepository;
import com.helpdesk.helpdesk.repository.CalendarReminderNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class CalendarService {

	private final CalendarObligationRepository calendarObligationRepository;
	private final CalendarReminderNotificationRepository calendarReminderNotificationRepository;
	private final UserRepository userRepository;

	public CalendarService(
		CalendarObligationRepository calendarObligationRepository,
		CalendarReminderNotificationRepository calendarReminderNotificationRepository,
		UserRepository userRepository
	) {
		this.calendarObligationRepository = calendarObligationRepository;
		this.calendarReminderNotificationRepository = calendarReminderNotificationRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<CalendarObligationResponse> listVisible(String email) {
		User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));

		OffsetDateTime now = OffsetDateTime.now();
		return loadVisibleObligations(user).stream()
			.map(obligation -> toResponse(obligation, now))
			.toList();
	}

	@Transactional
	public CalendarObligationResponse create(CreateCalendarObligationRequest request) {
		User createdBy = userRepository.findByEmailIgnoreCase(normalizeEmail(request.createdByEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela obrigação não encontrado."));
		ensureAdmin(createdBy, "Somente administradores podem criar obrigações no calendário.");
		validateDates(request.dueAt(), request.reminderAt());
		User recipient = resolveRecipient(request.recipientDocumentNumber());

		CalendarObligation obligation = new CalendarObligation();
		obligation.setCompanyOwner(createdBy);
		obligation.setCreatedBy(createdBy);
		obligation.setRecipient(recipient);
		obligation.setTitle(request.title().trim());
		obligation.setDescription(blankToNull(request.description()));
		obligation.setDueAt(request.dueAt());
		obligation.setReminderAt(request.reminderAt());

		return toResponse(calendarObligationRepository.save(obligation), OffsetDateTime.now());
	}

	@Transactional
	public void complete(UUID obligationId, String email) {
		User completedBy = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela conclusão não encontrado."));

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		ensureCanCompleteObligation(completedBy, obligation);

		if (obligation.getCompletedAt() == null) {
			obligation.setCompletedAt(OffsetDateTime.now());
			calendarObligationRepository.save(obligation);
		}
	}

	@Transactional
	public CalendarObligationResponse update(UUID obligationId, UpdateCalendarObligationRequest request) {
		User updatedBy = userRepository.findByEmailIgnoreCase(normalizeEmail(request.updatedByEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela atualização não encontrado."));
		ensureAdmin(updatedBy, "Somente administradores podem editar obrigações do calendário.");
		validateDates(request.dueAt(), request.reminderAt());

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		ensureAdminOwnsObligation(updatedBy, obligation);
		User recipient = resolveRecipient(request.recipientDocumentNumber());

		boolean scheduleChanged = !obligation.getDueAt().isEqual(request.dueAt())
			|| isDifferent(obligation.getReminderAt(), request.reminderAt())
			|| !obligation.getRecipient().getId().equals(recipient.getId());

		obligation.setTitle(request.title().trim());
		obligation.setDescription(blankToNull(request.description()));
		obligation.setDueAt(request.dueAt());
		obligation.setReminderAt(request.reminderAt());
		obligation.setRecipient(recipient);

		CalendarObligation savedObligation = calendarObligationRepository.save(obligation);

		if (scheduleChanged && savedObligation.getCompletedAt() == null) {
			calendarReminderNotificationRepository.deleteByObligationId(savedObligation.getId());
		}

		return toResponse(savedObligation, OffsetDateTime.now());
	}

	@Transactional
	public void delete(UUID obligationId, String email) {
		User deletedBy = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela exclusão não encontrado."));
		ensureAdmin(deletedBy, "Somente administradores podem excluir obrigações do calendário.");

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		ensureAdminOwnsObligation(deletedBy, obligation);

		calendarObligationRepository.delete(obligation);
	}

	private CalendarObligationResponse toResponse(CalendarObligation obligation, OffsetDateTime now) {
		String companyName = obligation.getCompanyOwner().getCompanyName();
		if (companyName == null || companyName.isBlank()) {
			companyName = obligation.getCompanyOwner().getFullName();
		}

		return new CalendarObligationResponse(
			obligation.getId(),
			obligation.getTitle(),
			obligation.getDescription(),
			obligation.getDueAt(),
			obligation.getReminderAt(),
			obligation.getCompletedAt(),
			obligation.getCreatedAt(),
			obligation.getCreatedBy().getFullName(),
			obligation.getRecipient().getFullName(),
			obligation.getRecipient().getDocumentNumber(),
			companyName,
			resolveStatus(obligation, now),
			isReminderActive(obligation, now)
		);
	}

	private String resolveStatus(CalendarObligation obligation, OffsetDateTime now) {
		if (obligation.getCompletedAt() != null) {
			return "COMPLETED";
		}

		OffsetDateTime dueAt = obligation.getDueAt();
		if (dueAt.isBefore(now)) {
			return "OVERDUE";
		}

		if (dueAt.toLocalDate().isEqual(now.toLocalDate())) {
			return "DUE_TODAY";
		}

		return "UPCOMING";
	}

	private boolean isReminderActive(CalendarObligation obligation, OffsetDateTime now) {
		return obligation.getCompletedAt() == null
			&& obligation.getReminderAt() != null
			&& !obligation.getReminderAt().isAfter(now);
	}

	private void validateDates(OffsetDateTime dueAt, OffsetDateTime reminderAt) {
		if (reminderAt != null && reminderAt.isAfter(dueAt)) {
			throw new IllegalArgumentException("O lembrete deve ocorrer antes ou no mesmo instante do prazo.");
		}
	}

	private void ensureAdminOwnsObligation(User admin, CalendarObligation obligation) {
		if (!obligation.getCompanyOwner().getId().equals(admin.getId())) {
			throw new IllegalArgumentException("Essa obrigação não pertence à sua empresa.");
		}
	}

	private void ensureCanCompleteObligation(User user, CalendarObligation obligation) {
		if (hasRole(user, "ADMIN")) {
			ensureAdminOwnsObligation(user, obligation);
			return;
		}

		if (!obligation.getRecipient().getId().equals(user.getId())) {
			throw new IllegalArgumentException("Você só pode concluir as obrigações atribuídas ao seu CPF.");
		}
	}

	private void ensureAdmin(User user, String message) {
		if (!hasRole(user, "ADMIN")) {
			throw new IllegalArgumentException(message);
		}
	}

	private List<CalendarObligation> loadVisibleObligations(User user) {
		if (hasRole(user, "ADMIN")) {
			return calendarObligationRepository.findVisibleByCompanyOwnerIdOrderByDueAtAsc(user.getId());
		}

		return calendarObligationRepository.findVisibleByRecipientIdOrderByDueAtAsc(user.getId());
	}

	private User resolveRecipient(String recipientDocumentNumber) {
		String normalizedDocumentNumber = normalizeDocumentNumber(recipientDocumentNumber);
		User recipient = userRepository.findByDocumentNumber(normalizedDocumentNumber)
			.or(() -> userRepository.findAll().stream()
				.filter(user -> normalizedDocumentNumber.equals(normalizeDocumentNumber(user.getDocumentNumber())))
				.findFirst()
				.flatMap(user -> userRepository.findById(user.getId())))
			.orElseThrow(() -> new NotFoundException("Nenhum usuário encontrado com o CPF informado."));

		if (recipient.getDeletedAt() != null || recipient.getStatus() == null || !"ACTIVE".equals(recipient.getStatus().name())) {
			throw new IllegalArgumentException("O usuário informado pelo CPF não está ativo no sistema.");
		}

		return recipient;
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream()
			.anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe o email do usuário.");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeDocumentNumber(String documentNumber) {
		if (documentNumber == null || documentNumber.isBlank()) {
			throw new IllegalArgumentException("Informe o CPF do destinatário.");
		}

		return documentNumber.replaceAll("\\D", "");
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		return value.trim();
	}

	private boolean isDifferent(OffsetDateTime firstValue, OffsetDateTime secondValue) {
		if (firstValue == null && secondValue == null) {
			return false;
		}

		if (firstValue == null || secondValue == null) {
			return true;
		}

		return !firstValue.isEqual(secondValue);
	}
}
