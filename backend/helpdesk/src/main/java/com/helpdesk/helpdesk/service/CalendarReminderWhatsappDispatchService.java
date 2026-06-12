package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.domain.CalendarObligation;
import com.helpdesk.helpdesk.domain.CalendarReminderNotification;
import com.helpdesk.helpdesk.domain.Company;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappOperationResponse;
import com.helpdesk.helpdesk.repository.CalendarObligationRepository;
import com.helpdesk.helpdesk.repository.CalendarReminderNotificationRepository;
import com.helpdesk.helpdesk.repository.CompanyRepository;

@Service
public class CalendarReminderWhatsappDispatchService {

	private static final Logger logger = LoggerFactory.getLogger(CalendarReminderWhatsappDispatchService.class);
	private static final Locale BRAZILIAN_PORTUGUESE = Locale.forLanguageTag("pt-BR");
	private static final DateTimeFormatter DATE_TIME_FORMATTER =
		DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm", BRAZILIAN_PORTUGUESE);

	private final CompanyRepository companyRepository;
	private final TenantExecutionService tenantExecutionService;
	private final CalendarObligationRepository calendarObligationRepository;
	private final CalendarReminderNotificationRepository calendarReminderNotificationRepository;
	private final WhatsappService whatsappService;

	public CalendarReminderWhatsappDispatchService(
		CompanyRepository companyRepository,
		TenantExecutionService tenantExecutionService,
		CalendarObligationRepository calendarObligationRepository,
		CalendarReminderNotificationRepository calendarReminderNotificationRepository,
		WhatsappService whatsappService
	) {
		this.companyRepository = companyRepository;
		this.tenantExecutionService = tenantExecutionService;
		this.calendarObligationRepository = calendarObligationRepository;
		this.calendarReminderNotificationRepository = calendarReminderNotificationRepository;
		this.whatsappService = whatsappService;
	}

	@Scheduled(cron = "${app.calendar-reminder.whatsapp.cron:0 * * * * *}")
	public void dispatchDueReminders() {
		OffsetDateTime now = OffsetDateTime.now();
		int createdCount = 0;
		int sentCount = 0;

		for (Company company : companyRepository.findAllByActiveTrueOrderByCompanyNameAsc()) {
			if (!isEligibleCompany(company)) {
				continue;
			}

			try {
				TenantDispatchResult result = tenantExecutionService.executeInTenantByOwnerUserId(
					company.getOwnerUser().getId(),
					() -> processTenantReminders(company.getOwnerUser(), now)
				);
				createdCount += result.createdCount();
				sentCount += result.sentCount();
			} catch (Exception exception) {
				logger.warn(
					"Falha ao processar lembretes de calendario via WhatsApp para a empresa {} (ownerUserId={}): {}",
					company.getCompanyName(),
					company.getOwnerUser() == null ? null : company.getOwnerUser().getId(),
					exception.getMessage()
				);
			}
		}

		if (createdCount > 0 || sentCount > 0) {
			logger.info(
				"Lembretes de calendario processados: notificacoesCriadas={}, mensagensWhatsappEnviadas={}",
				createdCount,
				sentCount
			);
		}
	}

	@Transactional
	TenantDispatchResult processTenantReminders(User companyOwner, OffsetDateTime now) {
		int createdCount = createMissingCalendarReminderNotificationsForCompanyOwner(companyOwner.getId(), now);
		int sentCount = 0;

		List<CalendarObligation> obligations = calendarObligationRepository.findVisibleByCompanyOwnerIdOrderByDueAtAsc(
			companyOwner.getId()
		);

		for (CalendarObligation obligation : obligations) {
			if (!shouldShowCalendarReminder(obligation, now)) {
				continue;
			}

			for (User recipient : obligation.getRecipients()) {
				if (recipient == null) {
					continue;
				}

				sentCount += dispatchReminderIfNeeded(companyOwner, obligation, recipient, now);
			}
		}

		return new TenantDispatchResult(createdCount, sentCount);
	}

	@Transactional
	public int createMissingCalendarReminderNotificationsForCompanyOwner(UUID companyOwnerId, OffsetDateTime now) {
		int createdCount = 0;
		List<CalendarObligation> obligations = calendarObligationRepository.findVisibleByCompanyOwnerIdOrderByDueAtAsc(companyOwnerId);

		for (CalendarObligation obligation : obligations) {
			if (!shouldShowCalendarReminder(obligation, now)) {
				continue;
			}

			for (User recipient : obligation.getRecipients()) {
				if (recipient == null) {
					continue;
				}

				if (ensureNotification(obligation, recipient) != null) {
					createdCount++;
				}
			}
		}

		return createdCount;
	}

	@Transactional
	public int dispatchReminderIfNeeded(
		User companyOwner,
		CalendarObligation obligation,
		User recipient,
		OffsetDateTime now
	) {
		if (companyOwner == null || obligation == null || recipient == null || !shouldShowCalendarReminder(obligation, now)) {
			return 0;
		}

		CalendarReminderNotification notification = findOrCreateNotification(obligation, recipient);
		if (notification == null || notification.getWhatsappSentAt() != null || !hasWhatsappTarget(recipient)) {
			return 0;
		}

		if (!sendWhatsappReminder(companyOwner, recipient, obligation, now)) {
			return 0;
		}

		notification.setWhatsappSentAt(now);
		calendarReminderNotificationRepository.save(notification);
		return 1;
	}

	private boolean sendWhatsappReminder(
		User companyOwner,
		User recipient,
		CalendarObligation obligation,
		OffsetDateTime now
	) {
		try {
			WhatsappOperationResponse response = whatsappService.sendMessage(
				companyOwner,
				resolveWhatsappTarget(recipient),
				buildReminderMessage(obligation, now)
			);
			if (!response.success()) {
				logger.warn(
					"Envio de lembrete de calendario via WhatsApp sem sucesso: obligationId={}, recipientId={}, detail={}",
					obligation.getId(),
					recipient.getId(),
					response.message()
				);
				return false;
			}
			return true;
		} catch (Exception exception) {
			logger.warn(
				"Falha ao enviar lembrete de calendario via WhatsApp: obligationId={}, recipientId={}, error={}",
				obligation.getId(),
				recipient.getId(),
				exception.getMessage()
			);
			return false;
		}
	}

	private String buildReminderMessage(CalendarObligation obligation, OffsetDateTime now) {
		String statusLabel = resolveStatusLabel(obligation, now);
		String description = normalizeText(obligation.getDescription());
		String companyName = resolveCompanyName(obligation.getCompanyOwner());
		StringBuilder message = new StringBuilder()
			.append("ChamAqui: lembrete de obrigacao\n")
			.append("Empresa: ").append(companyName).append('\n')
			.append("Obrigacao: ").append(normalizeText(obligation.getTitle())).append('\n')
			.append("Status: ").append(statusLabel).append('\n')
			.append("Vencimento: ").append(DATE_TIME_FORMATTER.format(obligation.getDueAt()));

		if (!description.isBlank()) {
			message.append('\n').append("Descricao: ").append(description);
		}

		message.append('\n').append("Acesse o ChamAqui para acompanhar os detalhes.");
		return message.toString();
	}

	private String resolveStatusLabel(CalendarObligation obligation, OffsetDateTime now) {
		if (obligation.getDueAt().isBefore(now)) {
			return "Atrasada";
		}
		if (obligation.getDueAt().toLocalDate().isEqual(now.toLocalDate())) {
			return "Vence hoje";
		}
		return "Lembrete agendado";
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

	private boolean isEligibleCompany(Company company) {
		return company != null
			&& company.isActive()
			&& company.getCompanyType() == CompanyType.RESPONDER
			&& company.getOwnerUser() != null;
	}

	private boolean hasWhatsappTarget(User recipient) {
		return !resolveWhatsappTarget(recipient).isBlank();
	}

	private String resolveWhatsappTarget(User recipient) {
		if (recipient == null) {
			return "";
		}

		if (recipient.getWhatsappTransportId() != null && !recipient.getWhatsappTransportId().isBlank()) {
			return recipient.getWhatsappTransportId().trim();
		}

		return recipient.getPhoneNumber() == null ? "" : recipient.getPhoneNumber().trim();
	}

	private CalendarReminderNotification findOrCreateNotification(CalendarObligation obligation, User recipient) {
		CalendarReminderNotification existingNotification = calendarReminderNotificationRepository
			.findByObligationIdAndRecipientId(obligation.getId(), recipient.getId())
			.orElse(null);

		if (existingNotification != null) {
			return existingNotification;
		}

		ensureNotification(obligation, recipient);
		return calendarReminderNotificationRepository.findByObligationIdAndRecipientId(obligation.getId(), recipient.getId())
			.orElse(null);
	}

	private CalendarReminderNotification ensureNotification(CalendarObligation obligation, User recipient) {
		if (calendarReminderNotificationRepository.findByObligationIdAndRecipientId(obligation.getId(), recipient.getId()).isPresent()) {
			return null;
		}

		CalendarReminderNotification notification = new CalendarReminderNotification();
		notification.setObligation(obligation);
		notification.setRecipient(recipient);
		return calendarReminderNotificationRepository.save(notification);
	}

	private String resolveCompanyName(User companyOwner) {
		String companyName = companyOwner == null ? "" : normalizeText(companyOwner.getCompanyName());
		if (!companyName.isBlank()) {
			return companyName;
		}
		return companyOwner == null ? "" : normalizeText(companyOwner.getFullName());
	}

	private String normalizeText(String value) {
		return value == null ? "" : value.trim();
	}

	private record TenantDispatchResult(int createdCount, int sentCount) {
	}
}
