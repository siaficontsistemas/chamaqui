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
import com.helpdesk.helpdesk.domain.TeamMembershipNotification;
import com.helpdesk.helpdesk.domain.TeamMembershipNotificationType;
import com.helpdesk.helpdesk.domain.TeamInvite;
import com.helpdesk.helpdesk.domain.Ticket;
import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.team.InviteTeamMemberRequest;
import com.helpdesk.helpdesk.dto.team.RespondTeamInviteRequest;
import com.helpdesk.helpdesk.dto.team.TeamInviteResponse;
import com.helpdesk.helpdesk.dto.team.TeamMemberResponse;
import com.helpdesk.helpdesk.dto.team.UpdateMemberSectorsRequest;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorMemberRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TeamMembershipNotificationRepository;
import com.helpdesk.helpdesk.repository.TeamInviteRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.TicketTransferNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class TeamService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final SectorRepository sectorRepository;
	private final SectorMemberRepository sectorMemberRepository;
	private final TeamInviteRepository teamInviteRepository;
	private final TeamMembershipNotificationRepository teamMembershipNotificationRepository;
	private final TicketRepository ticketRepository;
	private final TicketTransferNotificationRepository ticketTransferNotificationRepository;

	public TeamService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		SectorRepository sectorRepository,
		SectorMemberRepository sectorMemberRepository,
		TeamInviteRepository teamInviteRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository,
		TicketRepository ticketRepository,
		TicketTransferNotificationRepository ticketTransferNotificationRepository
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.sectorRepository = sectorRepository;
		this.sectorMemberRepository = sectorMemberRepository;
		this.teamInviteRepository = teamInviteRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
		this.ticketRepository = ticketRepository;
		this.ticketTransferNotificationRepository = ticketTransferNotificationRepository;
	}

	@Transactional(readOnly = true)
	public List<TeamMemberResponse> listMembers(String email) {
		List<SectorMember> sectorMembers = resolveVisibleSectorMembers(email);
		Map<UUID, List<UUID>> sectorsByUserId = sectorMembers.stream()
			.collect(Collectors.groupingBy(
				member -> member.getUser().getId(),
				Collectors.mapping(member -> member.getSector().getId(), Collectors.toList())
			));
		Map<UUID, User> teamUsersById = sectorMembers.stream()
			.map(SectorMember::getUser)
			.collect(Collectors.toMap(User::getId, Function.identity(), (firstUser, ignoredUser) -> firstUser));

		return teamUsersById.values().stream()
			.sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
			.map(user -> new TeamMemberResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				primaryRole(user),
				user.getStatus().name(),
				sectorsByUserId.getOrDefault(user.getId(), List.of())
			))
			.toList();
	}

	private List<SectorMember> resolveVisibleSectorMembers(String email) {
		if (email == null || email.isBlank()) {
			return sectorMemberRepository.findAllByOrderByAssignedAtAsc();
		}

		String normalizedEmail = normalizeEmail(email);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela consulta não encontrado."));

		List<UUID> visibleSectorIds =
			(hasRole(user, "ADMIN")
				? sectorRepository.findVisibleToAdminByEmail(normalizedEmail)
				: hasRole(user, "EMPLOYEE")
					? sectorRepository.findVisibleToMemberByEmail(normalizedEmail)
					: List.<Sector>of()
			).stream()
				.map(Sector::getId)
				.toList();

		if (visibleSectorIds.isEmpty()) {
			return List.of();
		}

		return sectorMemberRepository.findBySectorIdInOrderByAssignedAtAsc(visibleSectorIds);
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listInvites() {
		return teamInviteRepository.findAllByOrderByCreatedAtDesc().stream()
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listReceivedInvites(String email) {
		return teamInviteRepository.findAllByEmailIgnoreCaseAndInviteeHiddenFalseOrderByCreatedAtDesc(normalizeEmail(email)).stream()
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listSentInvites(String email) {
		return teamInviteRepository.findAllByInvitedByEmailIgnoreCaseAndInviterHiddenFalseOrderByCreatedAtDesc(normalizeEmail(email)).stream()
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional
	public TeamInviteResponse invite(InviteTeamMemberRequest request) {
		String invitedByEmail = normalizeEmail(request.invitedByEmail());
		String invitedEmail = normalizeEmail(request.email());
		User invitedBy = userRepository.findByEmailIgnoreCase(invitedByEmail)
			.orElseThrow(() -> new NotFoundException("Usuário responsável pelo convite não encontrado."));
		userRepository.findByEmailIgnoreCase(invitedEmail)
			.orElseThrow(() -> new NotFoundException("Nenhum usuário encontrado com o email informado para receber o convite."));

		if (invitedByEmail.equals(invitedEmail)) {
			throw new IllegalArgumentException("Você não pode convidar o próprio email para participar da equipe.");
		}
		if (teamInviteRepository.existsByEmailIgnoreCaseAndStatusAndInviteeHiddenFalse(invitedEmail, InviteStatus.PENDING)) {
			throw new IllegalArgumentException("Já existe um convite pendente para esse email.");
		}

		List<Sector> sectors = loadSectors(request.sectorIds());

		TeamInvite invite = new TeamInvite();
		invite.setInvitedName(request.invitedName().trim());
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
		User invitedUser = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
			.orElseThrow(() -> new NotFoundException("Usuário convidado não encontrado."));

		ensureInviteCanBeAnswered(invite, invitedUser);

		Role employeeRole = roleRepository.findByCode("EMPLOYEE")
			.orElseThrow(() -> new NotFoundException("Perfil de funcionário não encontrado."));
		invitedUser.getRoles().add(employeeRole);

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
		User invitedUser = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
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
		User assignedBy = userRepository.findByEmailIgnoreCase(request.assignedByEmail().trim())
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela atribuição não encontrado."));
		if (request.sectorIds().isEmpty()) {
			throw new IllegalArgumentException(
				"Para remover o funcionário da empresa inteira, use a ação de remoção da empresa."
			);
		}

		List<SectorMember> currentMemberships = sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId());
		Set<UUID> nextSectorIds = request.sectorIds().stream().collect(Collectors.toCollection(LinkedHashSet::new));
		List<Sector> removedSectors = currentMemberships.stream()
			.map(SectorMember::getSector)
			.filter(sector -> !nextSectorIds.contains(sector.getId()))
			.toList();
		List<Sector> sectors = loadSectors(request.sectorIds());

		sectorMemberRepository.deleteByUserId(member.getId());

		for (Sector sector : sectors) {
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
		User removedBy = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela remoção não encontrado."));

		if (!hasRole(member, "EMPLOYEE")) {
			throw new IllegalArgumentException("Somente funcionários podem ser removidos da empresa.");
		}
		if (member.getId().equals(removedBy.getId())) {
			throw new IllegalArgumentException("Você não pode remover a si mesmo da empresa.");
		}

		sectorMemberRepository.deleteByUserId(member.getId());
		clearTicketAccessForUser(member, List.of());
		member.getRoles().removeIf(role -> "EMPLOYEE".equalsIgnoreCase(role.getCode()));
		userRepository.save(member);
		createCompanyRemovalNotification(member, removedBy);

		return listMembers(email);
	}

	@Transactional
	public void leaveSector(UUID sectorId, String email) {
		User member = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Funcionário não encontrado."));

		if (!hasRole(member, "EMPLOYEE")) {
			throw new IllegalArgumentException("Somente funcionários podem sair de um setor.");
		}

		SectorMember membership = sectorMemberRepository.findByUserIdAndSectorId(member.getId(), sectorId)
			.orElseThrow(() -> new NotFoundException("Você não participa do setor informado."));

		sectorMemberRepository.delete(membership);
		clearTicketAccessForUser(member, List.of(sectorId));

		if (!sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).isEmpty()) {
			return;
		}

		member.getRoles().removeIf(role -> "EMPLOYEE".equalsIgnoreCase(role.getCode()));
		ensureHasDefaultRole(member);
		userRepository.save(member);
	}

	private List<Sector> loadSectors(List<UUID> sectorIds) {
		List<Sector> sectors = sectorRepository.findAllById(sectorIds);
		Map<UUID, Sector> sectorById = sectors.stream()
			.collect(Collectors.toMap(Sector::getId, Function.identity()));
		if (sectorById.size() != sectorIds.size()) {
			throw new NotFoundException("Um ou mais setores informados não foram encontrados.");
		}
		return sectorIds.stream()
			.map(sectorById::get)
			.toList();
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

	private Role loadDefaultUserRole() {
		return roleRepository.findByCode("USER")
			.orElseThrow(() -> new NotFoundException("Perfil padrão de usuário não encontrado."));
	}

	private TeamInvite loadInvite(UUID inviteId) {
		return teamInviteRepository.findWithDetailsById(inviteId)
			.orElseThrow(() -> new NotFoundException("Convite não encontrado."));
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
}
