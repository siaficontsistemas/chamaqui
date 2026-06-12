package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CalendarObligation;
import com.helpdesk.helpdesk.domain.CalendarObligationPriority;
import com.helpdesk.helpdesk.domain.CompanyPartnership;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.calendar.CalendarLinkedCompanyResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarLinkedTicketResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarObligationResponse;
import com.helpdesk.helpdesk.dto.calendar.CalendarTicketSearchResponse;
import com.helpdesk.helpdesk.dto.calendar.CreateCalendarObligationRequest;
import com.helpdesk.helpdesk.dto.calendar.MoveCalendarObligationCompanyRequest;
import com.helpdesk.helpdesk.dto.calendar.UpdateCalendarObligationRequest;
import com.helpdesk.helpdesk.dto.calendar.UpdateCalendarObligationTicketsRequest;
import com.helpdesk.helpdesk.repository.CalendarObligationRepository;
import com.helpdesk.helpdesk.repository.CalendarReminderNotificationRepository;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class CalendarService {

	private final CalendarObligationRepository calendarObligationRepository;
	private final CalendarReminderNotificationRepository calendarReminderNotificationRepository;
	private final CompanyPartnershipRepository companyPartnershipRepository;
	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;

	public CalendarService(
		CalendarObligationRepository calendarObligationRepository,
		CalendarReminderNotificationRepository calendarReminderNotificationRepository,
		CompanyPartnershipRepository companyPartnershipRepository,
		TicketRepository ticketRepository,
		UserRepository userRepository,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService
	) {
		this.calendarObligationRepository = calendarObligationRepository;
		this.calendarReminderNotificationRepository = calendarReminderNotificationRepository;
		this.companyPartnershipRepository = companyPartnershipRepository;
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
	}

	@Transactional(readOnly = true)
	public List<CalendarObligationResponse> listVisible(String email) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");

		OffsetDateTime now = OffsetDateTime.now();
		return loadVisibleObligations(user).stream()
			.map(obligation -> toResponse(obligation, now))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<CalendarLinkedCompanyResponse> listLinkedCompanies(String email) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");

		return loadVisibleLinkedCompanies(user).stream()
			.map(company -> new CalendarLinkedCompanyResponse(
				company.getId(),
				resolveCompanyName(company),
				company.getCompanyType() == null ? null : company.getCompanyType().name()
			))
			.toList();
	}

	@Transactional(readOnly = true)
	public CalendarTicketSearchResponse searchTickets(String email, String query, int offset, int limit) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");

		int sanitizedOffset = Math.max(offset, 0);
		int sanitizedLimit = Math.min(Math.max(limit, 1), 50);
		int page = sanitizedOffset / sanitizedLimit;

		List<CalendarLinkedTicketResponse> tickets = ticketRepository.searchVisibleByEmail(
			user.getEmail(),
			normalizeSearchQuery(query),
			PageRequest.of(page, sanitizedLimit + 1)
		).stream()
			.skip(sanitizedOffset % sanitizedLimit)
			.limit(sanitizedLimit + 1L)
			.map(this::toLinkedTicketResponse)
			.toList();

		boolean hasMore = tickets.size() > sanitizedLimit;
		return new CalendarTicketSearchResponse(
			hasMore ? tickets.subList(0, sanitizedLimit) : tickets,
			hasMore
		);
	}

	@Transactional
	public CalendarObligationResponse create(CreateCalendarObligationRequest request) {
		User createdBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.createdByEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela obrigação não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(createdBy, "Esse usuário não pertence ao tenant atual.");
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			createdBy.getId(),
			"Somente a empresa do tenant atual pode criar obrigações neste calendário."
		);
		ensureAdmin(createdBy, "Somente administradores podem criar obrigações no calendário.");
		validateDates(request.dueAt(), request.reminderAt());
		Set<User> recipients = resolveRecipients(request.recipientDocumentNumbers(), createdBy.getId());
		User linkedCompanyOwner = resolveLinkedCompanyOwner(createdBy, request.linkedCompanyOwnerId());
		Set<Ticket> linkedTickets = resolveLinkedTickets(request.linkedTicketIds(), createdBy.getEmail());

		CalendarObligation obligation = new CalendarObligation();
		obligation.setCompanyOwner(createdBy);
		obligation.setCreatedBy(createdBy);
		obligation.setLinkedCompanyOwner(linkedCompanyOwner);
		obligation.setRecipients(recipients);
		obligation.setLinkedTickets(linkedTickets);
		obligation.setTitle(request.title().trim());
		obligation.setDescription(blankToNull(request.description()));
		obligation.setPriority(resolvePriority(request.priority()));
		obligation.setDueAt(request.dueAt());
		obligation.setReminderAt(request.reminderAt());

		return toResponse(calendarObligationRepository.save(obligation), OffsetDateTime.now());
	}

	@Transactional
	public void complete(UUID obligationId, String email) {
		User completedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela conclusão não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(completedBy, "Esse usuário não pertence ao tenant atual.");

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			obligation.getCompanyOwner().getId(),
			"Essa obrigação não pertence ao tenant atual."
		);
		ensureCanCompleteObligation(completedBy, obligation);

		if (obligation.getCompletedAt() == null) {
			obligation.setCompletedAt(OffsetDateTime.now());
			calendarObligationRepository.save(obligation);
		}
	}

	@Transactional
	public CalendarObligationResponse update(UUID obligationId, UpdateCalendarObligationRequest request) {
		User updatedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.updatedByEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela atualização não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(updatedBy, "Esse usuário não pertence ao tenant atual.");
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			updatedBy.getId(),
			"Somente a empresa do tenant atual pode editar obrigações neste calendário."
		);
		ensureAdmin(updatedBy, "Somente administradores podem editar obrigações do calendário.");
		validateDates(request.dueAt(), request.reminderAt());

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			obligation.getCompanyOwner().getId(),
			"Essa obrigação não pertence ao tenant atual."
		);
		ensureAdminOwnsObligation(updatedBy, obligation);
		Set<User> recipients = resolveRecipients(request.recipientDocumentNumbers(), updatedBy.getId());
		User linkedCompanyOwner = resolveLinkedCompanyOwner(updatedBy, request.linkedCompanyOwnerId());
		Set<Ticket> linkedTickets = resolveLinkedTickets(request.linkedTicketIds(), updatedBy.getEmail());

		boolean scheduleChanged = !obligation.getDueAt().isEqual(request.dueAt())
			|| isDifferent(obligation.getReminderAt(), request.reminderAt())
			|| !hasSameRecipients(obligation.getRecipients(), recipients);

		obligation.setTitle(request.title().trim());
		obligation.setDescription(blankToNull(request.description()));
		obligation.setPriority(resolvePriority(request.priority()));
		obligation.setDueAt(request.dueAt());
		obligation.setReminderAt(request.reminderAt());
		obligation.setLinkedCompanyOwner(linkedCompanyOwner);
		obligation.setRecipients(recipients);
		obligation.setLinkedTickets(linkedTickets);

		CalendarObligation savedObligation = calendarObligationRepository.save(obligation);

		if (scheduleChanged && savedObligation.getCompletedAt() == null) {
			calendarReminderNotificationRepository.deleteByObligationId(savedObligation.getId());
		}

		return toResponse(savedObligation, OffsetDateTime.now());
	}

	@Transactional
	public CalendarObligationResponse updateLinkedTickets(UUID obligationId, UpdateCalendarObligationTicketsRequest request) {
		User updatedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.updatedByEmail()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela atualização não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(updatedBy, "Esse usuário não pertence ao tenant atual.");
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			updatedBy.getId(),
			"Somente a empresa do tenant atual pode vincular chamados neste calendário."
		);
		ensureAdmin(updatedBy, "Somente administradores podem vincular chamados às obrigações do calendário.");

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			obligation.getCompanyOwner().getId(),
			"Essa obrigação não pertence ao tenant atual."
		);
		ensureAdminOwnsObligation(updatedBy, obligation);

		obligation.setLinkedTickets(resolveLinkedTickets(request.linkedTicketIds(), updatedBy.getEmail()));
		return toResponse(calendarObligationRepository.save(obligation), OffsetDateTime.now());
	}

	@Transactional
	public CalendarObligationResponse moveToLinkedCompany(UUID obligationId, MoveCalendarObligationCompanyRequest request) {
		User movedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.email()))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela movimentação não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(movedBy, "Esse usuário não pertence ao tenant atual.");

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			obligation.getCompanyOwner().getId(),
			"Essa obrigação não pertence ao tenant atual."
		);
		ensureCanMoveObligation(movedBy, obligation);

		User calendarCompanyOwner = obligation.getCompanyOwner();
		User linkedCompanyOwner = resolveLinkedCompanyOwner(calendarCompanyOwner, request.linkedCompanyOwnerId());
		obligation.setLinkedCompanyOwner(linkedCompanyOwner);

		return toResponse(calendarObligationRepository.save(obligation), OffsetDateTime.now());
	}

	@Transactional
	public void delete(UUID obligationId, String email) {
		User deletedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela exclusão não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(deletedBy, "Esse usuário não pertence ao tenant atual.");
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			deletedBy.getId(),
			"Somente a empresa do tenant atual pode excluir obrigações deste calendário."
		);
		ensureAdmin(deletedBy, "Somente administradores podem excluir obrigações do calendário.");

		CalendarObligation obligation = calendarObligationRepository.findDetailedById(obligationId)
			.orElseThrow(() -> new NotFoundException("Obrigação não encontrada."));
		tenantAccessService.ensureCompanyMatchesCurrentTenant(
			obligation.getCompanyOwner().getId(),
			"Essa obrigação não pertence ao tenant atual."
		);
		ensureAdminOwnsObligation(deletedBy, obligation);

		calendarObligationRepository.delete(obligation);
	}

	private CalendarObligationResponse toResponse(CalendarObligation obligation, OffsetDateTime now) {
		String companyName = resolveCompanyName(obligation.getCompanyOwner());
		User linkedCompanyOwner = obligation.getLinkedCompanyOwner() != null
			? obligation.getLinkedCompanyOwner()
			: obligation.getCompanyOwner();

		return new CalendarObligationResponse(
			obligation.getId(),
			obligation.getTitle(),
			obligation.getDescription(),
			obligation.getPriority() == null ? CalendarObligationPriority.MEDIUM.name() : obligation.getPriority().name(),
			obligation.getDueAt(),
			obligation.getReminderAt(),
			obligation.getCompletedAt(),
			obligation.getCreatedAt(),
			obligation.getUpdatedAt(),
			obligation.getCreatedBy().getFullName(),
			obligation.getRecipients().stream()
				.map(User::getFullName)
				.filter(name -> name != null && !name.isBlank())
				.toList(),
			obligation.getRecipients().stream()
				.map(User::getDocumentNumber)
				.filter(document -> document != null && !document.isBlank())
				.toList(),
			companyName,
			linkedCompanyOwner.getId(),
			resolveCompanyName(linkedCompanyOwner),
			obligation.getLinkedTickets().stream()
				.sorted(Comparator.comparing(Ticket::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
					.thenComparing(Ticket::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.map(this::toLinkedTicketResponse)
				.toList(),
			resolveStatus(obligation, now),
			isReminderActive(obligation, now)
		);
	}

	private CalendarLinkedTicketResponse toLinkedTicketResponse(Ticket ticket) {
		String responsibleName = ticket.getAssignedTo() != null && ticket.getAssignedTo().getFullName() != null
			? ticket.getAssignedTo().getFullName()
			: ticket.getRequester() != null
				? ticket.getRequester().getFullName()
				: "Não informado";
		String statusCode = ticket.getStatus() == null || ticket.getStatus().getCode() == null
			? "UNKNOWN"
			: ticket.getStatus().getCode();
		String statusName = ticket.getStatus() == null || ticket.getStatus().getName() == null
			? "Não informado"
			: ticket.getStatus().getName();

		return new CalendarLinkedTicketResponse(
			ticket.getId(),
			ticket.getProtocol(),
			ticket.getTitle(),
			statusCode,
			statusName,
			responsibleName
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

	private void ensureCanMoveObligation(User user, CalendarObligation obligation) {
		if (hasRole(user, "ADMIN")) {
			User calendarCompanyOwner = resolveCalendarCompanyOwner(user);
			if (calendarCompanyOwner.getId().equals(obligation.getCompanyOwner().getId())) {
				return;
			}
		}

		if (hasRole(user, "EMPLOYEE")) {
			User calendarCompanyOwner = resolveCalendarCompanyOwner(user);
			boolean isRecipient = obligation.getRecipients().stream()
				.anyMatch(recipient -> recipient.getId().equals(user.getId()));
			if (calendarCompanyOwner.getId().equals(obligation.getCompanyOwner().getId()) && isRecipient) {
				return;
			}
		}

		throw new IllegalArgumentException("Você não tem permissão para mover essa obrigação entre empresas.");
	}

	private void ensureCanCompleteObligation(User user, CalendarObligation obligation) {
		if (hasRole(user, "ADMIN")) {
			ensureAdminOwnsObligation(user, obligation);
			return;
		}

		boolean isRecipient = obligation.getRecipients().stream()
			.anyMatch(recipient -> recipient.getId().equals(user.getId()));
		if (!isRecipient) {
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
			return calendarObligationRepository.findVisibleByCompanyOwnerIdOrderByDueAtAsc(resolveCalendarCompanyOwner(user).getId());
		}

		return calendarObligationRepository.findVisibleByRecipientIdOrderByDueAtAsc(user.getId());
	}

	private List<User> loadVisibleLinkedCompanies(User user) {
		if (hasRole(user, "ADMIN")) {
			return loadAvailableLinkedCompanies(resolveCalendarCompanyOwner(user));
		}

		Map<UUID, User> companiesById = new LinkedHashMap<>();
		for (CalendarObligation obligation : loadVisibleObligations(user)) {
			User linkedCompany = obligation.getLinkedCompanyOwner() != null
				? obligation.getLinkedCompanyOwner()
				: obligation.getCompanyOwner();
			if (linkedCompany != null) {
				companiesById.put(linkedCompany.getId(), linkedCompany);
			}
		}

		if (companiesById.isEmpty()) {
			User companyOwner = resolveCalendarCompanyOwner(user);
			companiesById.put(companyOwner.getId(), companyOwner);
		}

		return companiesById.values().stream()
			.sorted(Comparator.comparing(this::resolveCompanyName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private User resolveCalendarCompanyOwner(User user) {
		if (user == null) {
			throw new NotFoundException("Usuário do calendário não encontrado.");
		}

		if (hasRole(user, "ADMIN")) {
			return user;
		}

		if (user.getCompanyOwner() != null) {
			return user.getCompanyOwner();
		}

		throw new IllegalArgumentException("Esse usuário não possui uma empresa respondedora vinculada ao calendário.");
	}

	private List<User> loadAvailableLinkedCompanies(User companyOwner) {
		Map<UUID, User> linkedCompaniesById = new LinkedHashMap<>();
		linkedCompaniesById.put(companyOwner.getId(), companyOwner);

		for (CompanyPartnership partnership : companyPartnershipRepository.findVisibleByCompanyId(companyOwner.getId())) {
			if (partnership.getStatus() != CompanyPartnershipStatus.ACCEPTED) {
				continue;
			}

			User linkedCompany = partnership.getRequesterCompany() != null
				&& partnership.getRequesterCompany().getId().equals(companyOwner.getId())
					? partnership.getTargetCompany()
					: partnership.getRequesterCompany();

			if (linkedCompany == null) {
				continue;
			}

			linkedCompaniesById.put(linkedCompany.getId(), linkedCompany);
		}

		return linkedCompaniesById.values().stream()
			.sorted(Comparator.comparing(this::resolveCompanyName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private User resolveLinkedCompanyOwner(User calendarCompanyOwner, UUID linkedCompanyOwnerId) {
		if (linkedCompanyOwnerId == null || linkedCompanyOwnerId.equals(calendarCompanyOwner.getId())) {
			return calendarCompanyOwner;
		}

		return loadAvailableLinkedCompanies(calendarCompanyOwner).stream()
			.filter(company -> linkedCompanyOwnerId.equals(company.getId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("A empresa escolhida não está vinculada a esse calendário."));
	}

	private CalendarObligationPriority resolvePriority(String priorityValue) {
		CalendarObligationPriority priority = CalendarObligationPriority.fromValue(priorityValue);
		if (priority == null) {
			return CalendarObligationPriority.MEDIUM;
		}

		return priority;
	}

	private String resolveCompanyName(User company) {
		if (company == null) {
			return "Empresa não informada";
		}

		String companyName = company.getCompanyName();
		if (companyName == null || companyName.isBlank()) {
			companyName = company.getFullName();
		}

		return companyName == null || companyName.isBlank() ? "Empresa não informada" : companyName.trim();
	}

	private Set<Ticket> resolveLinkedTickets(List<UUID> linkedTicketIds, String email) {
		if (linkedTicketIds == null || linkedTicketIds.isEmpty()) {
			return new LinkedHashSet<>();
		}

		Set<Ticket> linkedTickets = new LinkedHashSet<>();
		for (UUID linkedTicketId : new LinkedHashSet<>(linkedTicketIds)) {
			if (linkedTicketId == null) {
				continue;
			}

			Ticket ticket = ticketRepository.findDetailedVisibleByIdAndEmail(linkedTicketId, normalizeEmail(email))
				.orElseThrow(() -> new NotFoundException("Um dos chamados vinculados não foi encontrado."));
			linkedTickets.add(ticket);
		}

		return linkedTickets;
	}

	private Set<User> resolveRecipients(List<String> recipientDocumentNumbers, UUID companyOwnerId) {
		if (recipientDocumentNumbers == null || recipientDocumentNumbers.isEmpty()) {
			throw new IllegalArgumentException("Informe pelo menos um CPF de destinatário.");
		}

		Set<User> recipients = new LinkedHashSet<>();
		Set<String> normalizedDocumentNumbers = new LinkedHashSet<>();
		for (String recipientDocumentNumber : recipientDocumentNumbers) {
			String normalizedDocumentNumber = normalizeDocumentNumber(recipientDocumentNumber);
			if (!normalizedDocumentNumbers.add(normalizedDocumentNumber)) {
				continue;
			}

			List<User> users = userRepository.findAllByDocumentNumberOrderByCreatedAtAsc(normalizedDocumentNumber);
			User recipient = users.stream()
				.filter(user -> belongsToCompanyOwner(user, companyOwnerId))
				.filter(user -> user.getDeletedAt() == null)
				.filter(user -> user.getStatus() != null && "ACTIVE".equals(user.getStatus().name()))
				.filter(user -> !hasRole(user, "ADMIN"))
				.findFirst()
				.or(() -> users.stream()
					.filter(user -> belongsToCompanyOwner(user, companyOwnerId))
					.filter(user -> user.getDeletedAt() == null)
					.filter(user -> user.getStatus() != null && "ACTIVE".equals(user.getStatus().name()))
					.findFirst())
				.orElseThrow(() -> new NotFoundException("Nenhum usuário encontrado com o CPF informado."));

			if (
				recipient.getDeletedAt() != null || recipient.getStatus() == null || !"ACTIVE".equals(recipient.getStatus().name())
			) {
				throw new IllegalArgumentException("Um dos usuários informados pelo CPF não está ativo no sistema.");
			}

			recipients.add(recipient);
		}

		if (recipients.isEmpty()) {
			throw new IllegalArgumentException("Informe pelo menos um CPF de destinatário.");
		}

		return recipients;
	}

	private boolean belongsToCompanyOwner(User user, UUID companyOwnerId) {
		if (user == null || companyOwnerId == null) {
			return false;
		}

		return companyOwnerId.equals(user.getId())
			|| (user.getCompanyOwner() != null && companyOwnerId.equals(user.getCompanyOwner().getId()));
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

	private String normalizeSearchQuery(String value) {
		if (value == null || value.isBlank()) {
			return "";
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

	private boolean hasSameRecipients(Collection<User> currentRecipients, Collection<User> nextRecipients) {
		Set<UUID> currentRecipientIds = currentRecipients == null
			? Set.of()
			: currentRecipients.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
		Set<UUID> nextRecipientIds = nextRecipients == null
			? Set.of()
			: nextRecipients.stream().map(User::getId).collect(java.util.stream.Collectors.toSet());
		return currentRecipientIds.equals(nextRecipientIds);
	}
}
