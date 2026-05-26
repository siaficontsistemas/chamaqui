package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.InviteStatus;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.SectorMember;
import com.helpdesk.helpdesk.domain.TeamInvite;
import com.helpdesk.helpdesk.domain.TeamMembershipNotification;
import com.helpdesk.helpdesk.domain.TeamMembershipNotificationType;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.team.InviteTeamMemberRequest;
import com.helpdesk.helpdesk.dto.team.RespondTeamInviteRequest;
import com.helpdesk.helpdesk.dto.team.TeamInviteResponse;
import com.helpdesk.helpdesk.dto.team.TeamMemberResponse;
import com.helpdesk.helpdesk.dto.team.UpdateMemberSectorsRequest;
import com.helpdesk.helpdesk.repository.CompanyMembershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorMemberRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TeamInviteRepository;
import com.helpdesk.helpdesk.repository.TeamMembershipNotificationRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.TicketTransferNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class TeamService {

	private final UserRepository userRepository;
	private final CompanyMembershipRepository companyMembershipRepository;
	private final RoleRepository roleRepository;
	private final SectorRepository sectorRepository;
	private final SectorMemberRepository sectorMemberRepository;
	private final TeamInviteRepository teamInviteRepository;
	private final TeamMembershipNotificationRepository teamMembershipNotificationRepository;
	private final TicketRepository ticketRepository;
	private final TicketTransferNotificationRepository ticketTransferNotificationRepository;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;

	public TeamService(
		UserRepository userRepository,
		CompanyMembershipRepository companyMembershipRepository,
		RoleRepository roleRepository,
		SectorRepository sectorRepository,
		SectorMemberRepository sectorMemberRepository,
		TeamInviteRepository teamInviteRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository,
		TicketRepository ticketRepository,
		TicketTransferNotificationRepository ticketTransferNotificationRepository,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService
	) {
		this.userRepository = userRepository;
		this.companyMembershipRepository = companyMembershipRepository;
		this.roleRepository = roleRepository;
		this.sectorRepository = sectorRepository;
		this.sectorMemberRepository = sectorMemberRepository;
		this.teamInviteRepository = teamInviteRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
		this.ticketRepository = ticketRepository;
		this.ticketTransferNotificationRepository = ticketTransferNotificationRepository;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
	}

	@Transactional(readOnly = true)
	public List<TeamMemberResponse> listMembers(String email) {
		String normalizedEmail = normalizeEmail(email);
		User viewer = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizedEmail)
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(viewer, "Esse usuário não pertence ao tenant atual.");
		List<SectorMember> sectorMembers = resolveVisibleSectorMembers(viewer);
		Map<UUID, List<UUID>> sectorsByUserId = sectorMembers.stream()
			.collect(Collectors.groupingBy(
				member -> member.getUser().getId(),
				Collectors.collectingAndThen(
					Collectors.mapping(
						member -> member.getSector().getId(),
						Collectors.toCollection(LinkedHashSet::new)
					),
					List::copyOf
				)
			));
		Map<UUID, User> teamUsersById = loadVisibleTeamUsers(viewer, sectorMembers).stream()
			.filter(user -> hasRole(user, "EMPLOYEE"))
			.collect(Collectors.toMap(User::getId, Function.identity(), (firstUser, ignoredUser) -> firstUser));

		return teamUsersById.values().stream()
			.sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
			.map(user -> new TeamMemberResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				user.getDocumentNumber(),
				primaryRole(user),
				user.getStatus().name(),
				sectorsByUserId.getOrDefault(user.getId(), List.of())
			))
			.toList();
	}

	private List<SectorMember> resolveVisibleSectorMembers(User user) {
		List<UUID> visibleSectorIds =
			(hasRole(user, "ADMIN")
				? sectorRepository.findVisibleToAdminByEmail(user.getEmail())
				: hasRole(user, "EMPLOYEE")
					? sectorRepository.findVisibleToMemberByEmail(user.getEmail())
					: List.<Sector>of()
			).stream()
				.map(Sector::getId)
				.toList();

		if (visibleSectorIds.isEmpty()) {
			return List.of();
		}

		return sectorMemberRepository.findBySectorIdInOrderByAssignedAtAsc(visibleSectorIds);
	}

	private List<User> loadVisibleTeamUsers(User viewer, List<SectorMember> sectorMembers) {
		if (hasRole(viewer, "ADMIN")) {
			Map<UUID, User> usersById = companyMembershipRepository.findByCompanyOwnerIdOrderByJoinedAtAsc(viewer.getId())
				.stream()
				.map(com.helpdesk.helpdesk.domain.CompanyMembership::getUser)
				.filter(user -> !user.getId().equals(viewer.getId()))
				.collect(Collectors.toMap(User::getId, Function.identity(), (firstUser, ignoredUser) -> firstUser));

			for (SectorMember sectorMember : sectorMembers) {
				User user = sectorMember.getUser();
				if (user == null || user.getId().equals(viewer.getId())) {
					continue;
				}
				usersById.putIfAbsent(user.getId(), user);
			}

			return List.copyOf(usersById.values());
		}

		return sectorMembers.stream()
			.map(SectorMember::getUser)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listInvites() {
		UUID tenantOwnerUserId = tenantAccessService.getCurrentTenantOwnerUserId().orElse(null);
		return teamInviteRepository.findAllByOrderByCreatedAtDesc().stream()
			.filter(invite -> tenantOwnerUserId == null
				|| (invite.getInvitedBy() != null && tenantOwnerUserId.equals(invite.getInvitedBy().getId())))
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listReceivedInvites(String email) {
		User viewer = loadUserByEmail(email, "Usuário responsável pela consulta não encontrado.");
		return teamInviteRepository.findAllByEmailIgnoreCaseAndInviteeHiddenFalseOrderByCreatedAtDesc(
			normalizeEmail(viewer.getEmail())
		).stream()
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listSentInvites(String email) {
		User viewer = loadUserByEmail(email, "Usuário responsável pela consulta não encontrado.");
		return teamInviteRepository.findAllByInvitedByEmailIgnoreCaseAndInviterHiddenFalseOrderByCreatedAtDesc(
			normalizeEmail(viewer.getEmail())
		).stream()
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional
	public TeamInviteResponse invite(InviteTeamMemberRequest request) {
		String invitedByEmail = normalizeEmail(request.invitedByEmail());
		User invitedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(invitedByEmail)
			.orElseThrow(() -> new NotFoundException("Usuário responsável pelo convite não encontrado."));
		ensureAdmin(invitedBy, "Somente administradores podem convidar funcionários para a equipe.");
		User invitedUser = findUserByDocumentNumber(
			request.documentNumber(),
			"Nenhum usuário encontrado com o CPF informado para receber o convite."
		);
		String invitedEmail = normalizeEmail(invitedUser.getEmail());

		if (invitedByEmail.equals(invitedEmail)) {
			throw new IllegalArgumentException("Você não pode convidar a si mesmo para participar da equipe.");
		}
		ensureInvitableUser(invitedUser);
		if (teamInviteRepository.existsByEmailIgnoreCaseAndStatusAndInviteeHiddenFalse(invitedEmail, InviteStatus.PENDING)) {
			throw new IllegalArgumentException("Já existe um convite pendente para esse funcionário.");
		}

		List<Sector> sectors = loadSectors(request.sectorIds());
		ensureAdminOwnsSectors(invitedBy, sectors);

		TeamInvite invite = new TeamInvite();
		invite.setInvitedName(invitedUser.getFullName());
		invite.setEmail(invitedEmail);
		invite.setInvitedBy(invitedBy);
		invite.setTokenHash(UUID.randomUUID().toString());
		invite.setExpiresAt(OffsetDateTime.now().plusDays(7));
		invite.getSectors().addAll(sectors);

		return toInviteResponse(teamInviteRepository.save(invite));
	}

	@Transactional
	public TeamInviteResponse acceptInvite(UUID inviteId, RespondTeamInviteRequest request) {
		TeamInvite invite = loadInvite(inviteId);
		User invitedUser = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.email()))
			.orElseThrow(() -> new NotFoundException("Usuário convidado não encontrado."));

		ensureInviteCanBeAnswered(invite, invitedUser);
		ensureCanJoinInvitingCompany(invitedUser);

		Role employeeRole = roleRepository.findByCode("EMPLOYEE")
			.orElseThrow(() -> new NotFoundException("Perfil de funcionário não encontrado."));
		ensureCompanyMembership(invitedUser, invite.getInvitedBy());
		if (invitedUser.getStatus() != null && !com.helpdesk.helpdesk.domain.UserStatus.ACTIVE.equals(invitedUser.getStatus())) {
			invitedUser.setStatus(com.helpdesk.helpdesk.domain.UserStatus.ACTIVE);
		}
		invitedUser.getRoles().add(employeeRole);
		userRepository.save(invitedUser);

		Set<UUID> assignedSectorIds = sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(invitedUser.getId()).stream()
			.map(member -> member.getSector().getId())
			.collect(Collectors.toCollection(LinkedHashSet::new));

		for (Sector sector : invite.getSectors()) {
			if (assignedSectorIds.contains(sector.getId())) {
				continue;
			}

			SectorMember sectorMember = new SectorMember();
			sectorMember.setSector(sector);
			sectorMember.setUser(invitedUser);
			sectorMember.setAssignedBy(invite.getInvitedBy());
			sectorMemberRepository.save(sectorMember);
		}

		invite.setAcceptedUser(invitedUser);
		invite.setAcceptedAt(OffsetDateTime.now());
		invite.setStatus(InviteStatus.ACCEPTED);
		return toInviteResponse(teamInviteRepository.save(invite));
	}

	@Transactional
	public TeamInviteResponse declineInvite(UUID inviteId, RespondTeamInviteRequest request) {
		TeamInvite invite = loadInvite(inviteId);
		User invitedUser = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(request.email()))
			.orElseThrow(() -> new NotFoundException("Usuário convidado não encontrado."));

		ensureInviteCanBeAnswered(invite, invitedUser);

		invite.setAcceptedUser(null);
		invite.setAcceptedAt(null);
		invite.setStatus(InviteStatus.CANCELED);
		return toInviteResponse(teamInviteRepository.save(invite));
	}

	@Transactional
	public void deleteNotification(UUID inviteId, String email) {
		TeamInvite invite = loadInvite(inviteId);
		String normalizedEmail = normalizeEmail(email);

		if (invite.getEmail().equalsIgnoreCase(normalizedEmail)) {
			invite.setInviteeHidden(true);
			teamInviteRepository.save(invite);
			return;
		}

		if (invite.getInvitedBy().getEmail().equalsIgnoreCase(normalizedEmail)) {
			invite.setInviterHidden(true);
			teamInviteRepository.save(invite);
			return;
		}

		throw new IllegalArgumentException("Essa notificação não pertence ao usuário informado.");
	}

	@Transactional
	public List<TeamMemberResponse> updateMemberSectors(UUID userId, UpdateMemberSectorsRequest request) {
		User member = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("Funcionário não encontrado."));
		User assignedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(request.assignedByEmail().trim())
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela atribuição não encontrado."));
		ensureAdmin(assignedBy, "Somente administradores podem alterar os setores dos funcionários.");
		ensureManagedEmployee(assignedBy, member, "Esse funcionário não pertence à empresa administrada por você.");
		if (!hasRole(member, "EMPLOYEE")) {
			throw new IllegalArgumentException("Somente funcionários podem ter setores atualizados.");
		}

		List<SectorMember> currentMemberships = sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).stream()
			.filter(currentMembership -> assignedBy.getId().equals(currentMembership.getSector().getCreatedBy().getId()))
			.toList();
		Set<UUID> nextSectorIds = request.sectorIds().stream().collect(Collectors.toCollection(LinkedHashSet::new));
		Map<UUID, SectorMember> currentMembershipBySectorId = currentMemberships.stream()
			.collect(Collectors.toMap(
				currentMembership -> currentMembership.getSector().getId(),
				Function.identity(),
				(firstMembership, ignoredMembership) -> firstMembership
			));
		List<Sector> removedSectors = currentMemberships.stream()
			.map(SectorMember::getSector)
			.filter(sector -> !nextSectorIds.contains(sector.getId()))
			.toList();
		List<Sector> sectors = loadSectors(request.sectorIds());
		ensureAdminOwnsSectors(assignedBy, sectors);

		List<SectorMember> membershipsToRemove = currentMemberships.stream()
			.filter(currentMembership -> !nextSectorIds.contains(currentMembership.getSector().getId()))
			.toList();
		if (!membershipsToRemove.isEmpty()) {
			sectorMemberRepository.deleteAll(membershipsToRemove);
		}

		for (Sector sector : sectors) {
			if (currentMembershipBySectorId.containsKey(sector.getId())) {
				continue;
			}

			SectorMember sectorMember = new SectorMember();
			sectorMember.setSector(sector);
			sectorMember.setUser(member);
			sectorMember.setAssignedBy(assignedBy);
			sectorMemberRepository.save(sectorMember);
		}

		clearTicketAccessForUser(member, removedSectors.stream().map(Sector::getId).toList());
		createSectorRemovalNotifications(member, assignedBy, removedSectors);

		return listMembers(request.assignedByEmail());
	}

	@Transactional
	public List<TeamMemberResponse> removeMemberFromCompany(UUID userId, String email) {
		User member = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("Funcionário não encontrado."));
		User removedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela remoção não encontrado."));
		ensureAdmin(removedBy, "Somente administradores podem remover funcionários da empresa.");
		ensureManagedEmployee(removedBy, member, "Esse funcionário não pertence à empresa administrada por você.");

		if (!hasRole(member, "EMPLOYEE")) {
			throw new IllegalArgumentException("Somente funcionários podem ser removidos da empresa.");
		}
		if (member.getId().equals(removedBy.getId())) {
			throw new IllegalArgumentException("Você não pode remover a si mesmo da empresa.");
		}

		List<SectorMember> membershipsToRemove = sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).stream()
			.filter(currentMembership -> removedBy.getId().equals(currentMembership.getSector().getCreatedBy().getId()))
			.toList();
		List<UUID> removedSectorIds = membershipsToRemove.stream()
			.map(currentMembership -> currentMembership.getSector().getId())
			.toList();
		if (!membershipsToRemove.isEmpty()) {
			sectorMemberRepository.deleteAll(membershipsToRemove);
		}
		companyMembershipRepository.deleteByUserIdAndCompanyOwnerId(member.getId(), removedBy.getId());
		clearTicketAccessForUser(member, removedSectorIds);
		refreshPrimaryCompanyOwner(member);
		removeEmployeeRoleIfWithoutCompanyMembershipsOrSectors(member);
		userRepository.save(member);
		createCompanyRemovalNotification(member, removedBy);

		return listMembers(email);
	}

	@Transactional
	public void deleteSector(UUID sectorId, String email) {
		User deletedBy = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela exclusão não encontrado."));
		ensureAdmin(deletedBy, "Somente administradores podem excluir setores.");

		Sector sector = sectorRepository.findById(sectorId)
			.orElseThrow(() -> new NotFoundException("Setor não encontrado."));
		ensureAdminOwnsSector(deletedBy, sector);

		List<SectorMember> memberships = sectorMemberRepository.findBySectorIdOrderByAssignedAtAsc(sectorId);
		Map<UUID, User> affectedMembersById = memberships.stream()
			.map(SectorMember::getUser)
			.collect(Collectors.toMap(User::getId, Function.identity(), (firstUser, ignoredUser) -> firstUser));

		for (User affectedMember : affectedMembersById.values()) {
			clearTicketAccessForUser(affectedMember, List.of(sectorId));
		}

		sectorMemberRepository.deleteAll(memberships);
		updateInvitesAfterSectorDeletion(sector);
		sector.setActive(false);
		sector.setArchivedAt(OffsetDateTime.now());
		sectorRepository.save(sector);

		for (User affectedMember : affectedMembersById.values()) {
			removeEmployeeRoleIfWithoutSectors(affectedMember);
		}

		List<TeamMembershipNotification> notifications = affectedMembersById.values().stream()
			.map(member -> createMembershipNotification(
				member,
				deletedBy,
				sector,
				TeamMembershipNotificationType.SECTOR_REMOVED
			))
			.toList();
		teamMembershipNotificationRepository.saveAll(notifications);
	}

	private List<Sector> loadSectors(List<UUID> sectorIds) {
		List<UUID> uniqueSectorIds = sectorIds.stream()
			.collect(Collectors.toCollection(LinkedHashSet::new))
			.stream()
			.toList();
		List<Sector> sectors = sectorRepository.findAllById(uniqueSectorIds);
		Map<UUID, Sector> sectorById = sectors.stream()
			.collect(Collectors.toMap(Sector::getId, Function.identity()));
		if (sectorById.size() != uniqueSectorIds.size()) {
			throw new NotFoundException("Um ou mais setores informados não foram encontrados.");
		}
		return uniqueSectorIds.stream()
			.map(sectorById::get)
			.toList();
	}

	private void updateInvitesAfterSectorDeletion(Sector sector) {
		List<TeamInvite> invites = teamInviteRepository.findAllBySectorsId(sector.getId());

		if (invites.isEmpty()) {
			return;
		}

		for (TeamInvite invite : invites) {
			invite.getSectors().removeIf(currentSector -> currentSector.getId().equals(sector.getId()));
			if (invite.getStatus() == InviteStatus.PENDING && invite.getSectors().isEmpty()) {
				invite.setStatus(InviteStatus.CANCELED);
			}
		}

		teamInviteRepository.saveAll(invites);
	}

	private void clearTicketAccessForUser(User member, List<UUID> sectorIds) {
		List<Ticket> affectedTickets = sectorIds.isEmpty()
			? ticketRepository.findTicketsAffectedByUserId(member.getId())
			: ticketRepository.findTicketsAffectedByUserIdAndSectorIdIn(member.getId(), sectorIds);

		if (affectedTickets.isEmpty()) {
			return;
		}

		OffsetDateTime now = OffsetDateTime.now();
		for (Ticket ticket : affectedTickets) {
			if (ticket.getAssignedTo() != null && ticket.getAssignedTo().getId().equals(member.getId())) {
				ticket.setAssignedTo(null);
			}

			if ((ticket.getPendingTransferTo() != null && ticket.getPendingTransferTo().getId().equals(member.getId()))
				|| (ticket.getPendingTransferRequestedBy() != null
					&& ticket.getPendingTransferRequestedBy().getId().equals(member.getId()))) {
				clearPendingTransfer(ticket);
				markTransferNotificationsAsDeclined(ticket, now);
			}
		}

		ticketRepository.saveAll(affectedTickets);
	}

	private void markTransferNotificationsAsDeclined(Ticket ticket, OffsetDateTime respondedAt) {
		List<TicketTransferNotification> pendingNotifications =
			ticketTransferNotificationRepository.findByTicketIdAndStatus(ticket.getId(), TicketTransferStatus.PENDING);

		if (pendingNotifications.isEmpty()) {
			return;
		}

		for (TicketTransferNotification pendingNotification : pendingNotifications) {
			pendingNotification.setStatus(TicketTransferStatus.DECLINED);
			pendingNotification.setRespondedAt(respondedAt);
		}

		ticketTransferNotificationRepository.saveAll(pendingNotifications);
	}

	private void clearPendingTransfer(Ticket ticket) {
		ticket.setPendingTransferTo(null);
		ticket.setPendingTransferRequestedBy(null);
		ticket.setPendingTransferRequestedAt(null);
	}

	private void createSectorRemovalNotifications(User member, User removedBy, List<Sector> removedSectors) {
		if (removedSectors.isEmpty()) {
			return;
		}

		List<TeamMembershipNotification> notifications = removedSectors.stream()
			.map(sector -> createMembershipNotification(
				member,
				removedBy,
				sector,
				TeamMembershipNotificationType.SECTOR_REMOVED
			))
			.toList();
		teamMembershipNotificationRepository.saveAll(notifications);
	}

	private void createCompanyRemovalNotification(User member, User removedBy) {
		teamMembershipNotificationRepository.save(
			createMembershipNotification(member, removedBy, null, TeamMembershipNotificationType.COMPANY_REMOVED)
		);
	}

	private TeamMembershipNotification createMembershipNotification(
		User member,
		User removedBy,
		Sector sector,
		TeamMembershipNotificationType type
	) {
		TeamMembershipNotification notification = new TeamMembershipNotification();
		notification.setRecipient(member);
		notification.setRemovedBy(removedBy);
		notification.setSector(sector);
		notification.setCompanyName(resolveCompanyName(removedBy, sector));
		notification.setType(type);
		return notification;
	}

	private String resolveCompanyName(User removedBy, Sector sector) {
		if (sector != null && sector.getCreatedBy() != null && sector.getCreatedBy().getCompanyName() != null
			&& !sector.getCreatedBy().getCompanyName().isBlank()) {
			return sector.getCreatedBy().getCompanyName();
		}
		if (removedBy.getCompanyName() != null && !removedBy.getCompanyName().isBlank()) {
			return removedBy.getCompanyName();
		}
		return removedBy.getFullName();
	}

	private void ensureHasDefaultRole(User user) {
		if (!user.getRoles().isEmpty()) {
			return;
		}

		user.getRoles().add(loadDefaultUserRole());
	}

	private void ensureAdmin(User user, String message) {
		if (!hasRole(user, "ADMIN")) {
			throw new IllegalArgumentException(message);
		}
	}

	private void ensureManagedEmployee(User admin, User member, String message) {
		boolean managesMember = companyMembershipRepository.existsByUserIdAndCompanyOwnerId(member.getId(), admin.getId())
			|| sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).stream()
				.anyMatch(sectorMember -> admin.getId().equals(sectorMember.getSector().getCreatedBy().getId()));
		if (!managesMember) {
			throw new IllegalArgumentException(message);
		}
	}

	private void ensureInvitableUser(User invitedUser) {
		if (hasRole(invitedUser, "ADMIN")) {
			throw new IllegalArgumentException("Administradores não podem ser convidados para entrar em setores como funcionários.");
		}
	}

	private void ensureAdminOwnsSectors(User admin, List<Sector> sectors) {
		for (Sector sector : sectors) {
			ensureAdminOwnsSector(admin, sector);
		}
	}

	private void ensureAdminOwnsSector(User admin, Sector sector) {
		if (!admin.getId().equals(sector.getCreatedBy().getId())) {
			throw new IllegalArgumentException("Você só pode gerenciar setores criados pela sua própria empresa.");
		}
	}

	private User findUserByDocumentNumber(String documentNumber, String notFoundMessage) {
		String normalizedDocumentNumber = normalizeDocumentNumber(documentNumber);
		List<User> users = userRepository.findAllByDocumentNumberOrderByCreatedAtAsc(normalizedDocumentNumber);

		return users.stream()
			.filter(user -> !hasRole(user, "ADMIN"))
			.findFirst()
			.or(() -> users.stream().findFirst())
			.orElseThrow(() -> new NotFoundException(notFoundMessage));
	}

	private String normalizeDocumentNumber(String documentNumber) {
		if (documentNumber == null) {
			return "";
		}
		return documentNumber.replaceAll("\\D", "");
	}

	private void removeEmployeeRoleIfWithoutSectors(User member) {
		if (!sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).isEmpty()) {
			return;
		}

		if (companyMembershipRepository.existsByUserIdAndCompanyType(member.getId(), com.helpdesk.helpdesk.domain.CompanyType.RESPONDER)) {
			return;
		}

		member.getRoles().removeIf(role -> "EMPLOYEE".equalsIgnoreCase(role.getCode()));
		ensureHasDefaultRole(member);
		userRepository.save(member);
	}

	private Role loadDefaultUserRole() {
		return roleRepository.findByCode("USER")
			.orElseThrow(() -> new NotFoundException("Perfil padrão de usuário não encontrado."));
	}

	private TeamInvite loadInvite(UUID inviteId) {
		return teamInviteRepository.findWithDetailsById(inviteId)
			.orElseThrow(() -> new NotFoundException("Convite não encontrado."));
	}

	private User loadUserByEmail(String email, String notFoundMessage) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException(notFoundMessage));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");
		return user;
	}

	private void ensureInviteCanBeAnswered(TeamInvite invite, User invitedUser) {
		if (!invite.getEmail().equalsIgnoreCase(invitedUser.getEmail())) {
			throw new IllegalArgumentException("Esse convite não pertence ao usuário informado.");
		}

		InviteStatus inviteStatus = currentStatus(invite);
		if (inviteStatus == InviteStatus.EXPIRED) {
			invite.setStatus(InviteStatus.EXPIRED);
			teamInviteRepository.save(invite);
			throw new IllegalArgumentException("Esse convite expirou e não pode mais ser respondido.");
		}
		if (inviteStatus != InviteStatus.PENDING) {
			throw new IllegalArgumentException("Esse convite já foi respondido anteriormente.");
		}
	}

	private void ensureCanJoinInvitingCompany(User invitedUser) {
		if (hasRole(invitedUser, "ADMIN")) {
			throw new IllegalArgumentException("Administradores não podem aceitar convites para atuar como funcionários.");
		}
	}

	private InviteStatus currentStatus(TeamInvite invite) {
		if (invite.getStatus() == InviteStatus.PENDING && invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
			return InviteStatus.EXPIRED;
		}
		return invite.getStatus();
	}

	private TeamInviteResponse toInviteResponse(TeamInvite invite) {
		return new TeamInviteResponse(
			invite.getId(),
			invite.getInvitedName(),
			invite.getEmail(),
			currentStatus(invite).name(),
			invite.getInvitedBy().getEmail(),
			invite.getInvitedBy().getFullName(),
			invite.getExpiresAt(),
			invite.getAcceptedAt(),
			invite.getUpdatedAt(),
			invite.getSectors().stream().map(Sector::getId).toList(),
			invite.getSectors().stream().map(Sector::getName).toList()
		);
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String primaryRole(User user) {
		return user.getRoles().stream()
			.sorted(Comparator.comparingInt(role -> switch (role.getCode()) {
				case "ADMIN" -> 0;
				case "EMPLOYEE" -> 1;
				default -> 2;
			}))
			.map(role -> role.getName())
			.findFirst()
			.orElse("Usuário");
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream()
			.anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private void ensureCompanyMembership(User user, User companyOwner) {
		if (companyMembershipRepository.existsByUserIdAndCompanyOwnerId(user.getId(), companyOwner.getId())) {
			if (user.getCompanyOwner() == null) {
				user.setCompanyOwner(companyOwner);
			}
			return;
		}

		com.helpdesk.helpdesk.domain.CompanyMembership membership = new com.helpdesk.helpdesk.domain.CompanyMembership();
		membership.setUser(user);
		membership.setCompanyOwner(companyOwner);
		companyMembershipRepository.save(membership);

		if (user.getCompanyOwner() == null) {
			user.setCompanyOwner(companyOwner);
		}
	}

	private void refreshPrimaryCompanyOwner(User user) {
		User currentCompanyOwner = user.getCompanyOwner();
		if (currentCompanyOwner != null
			&& companyMembershipRepository.existsByUserIdAndCompanyOwnerId(user.getId(), currentCompanyOwner.getId())) {
			return;
		}

		User nextCompanyOwner = companyMembershipRepository.findByUserIdOrderByJoinedAtAsc(user.getId()).stream()
			.map(com.helpdesk.helpdesk.domain.CompanyMembership::getCompanyOwner)
			.findFirst()
			.orElse(null);
		user.setCompanyOwner(nextCompanyOwner);
	}

	private void removeEmployeeRoleIfWithoutCompanyMembershipsOrSectors(User member) {
		if (!sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).isEmpty()) {
			return;
		}
		if (companyMembershipRepository.existsByUserIdAndCompanyType(member.getId(), com.helpdesk.helpdesk.domain.CompanyType.RESPONDER)) {
			return;
		}

		member.getRoles().removeIf(role -> "EMPLOYEE".equalsIgnoreCase(role.getCode()));
		ensureHasDefaultRole(member);
	}
}
