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
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.team.InviteTeamMemberRequest;
import com.helpdesk.helpdesk.dto.team.RespondTeamInviteRequest;
import com.helpdesk.helpdesk.dto.team.TeamInviteResponse;
import com.helpdesk.helpdesk.dto.team.TeamMemberResponse;
import com.helpdesk.helpdesk.dto.team.UpdateMemberSectorsRequest;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorMemberRepository;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.TeamInviteRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class TeamService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final SectorRepository sectorRepository;
	private final SectorMemberRepository sectorMemberRepository;
	private final TeamInviteRepository teamInviteRepository;

	public TeamService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		SectorRepository sectorRepository,
		SectorMemberRepository sectorMemberRepository,
		TeamInviteRepository teamInviteRepository
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.sectorRepository = sectorRepository;
		this.sectorMemberRepository = sectorMemberRepository;
		this.teamInviteRepository = teamInviteRepository;
	}

	@Transactional(readOnly = true)
	public List<TeamMemberResponse> listMembers() {
		Map<UUID, List<UUID>> sectorsByUserId = sectorMemberRepository.findAllByOrderByAssignedAtAsc().stream()
			.collect(Collectors.groupingBy(
				member -> member.getUser().getId(),
				Collectors.mapping(member -> member.getSector().getId(), Collectors.toList())
			));

		return userRepository.findDistinctByRolesCodeInOrderByFullNameAsc(List.of("ADMIN", "EMPLOYEE")).stream()
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

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listInvites() {
		return teamInviteRepository.findAllByOrderByCreatedAtDesc().stream()
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listReceivedInvites(String email) {
		return teamInviteRepository.findAllByEmailIgnoreCaseOrderByCreatedAtDesc(normalizeEmail(email)).stream()
			.map(this::toInviteResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<TeamInviteResponse> listSentInvites(String email) {
		return teamInviteRepository.findAllByInvitedByEmailIgnoreCaseOrderByCreatedAtDesc(normalizeEmail(email)).stream()
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
		if (teamInviteRepository.existsByEmailIgnoreCaseAndStatus(invitedEmail, InviteStatus.PENDING)) {
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
	public List<TeamMemberResponse> updateMemberSectors(UUID userId, UpdateMemberSectorsRequest request) {
		User member = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("Funcionário não encontrado."));
		User assignedBy = userRepository.findByEmailIgnoreCase(request.assignedByEmail().trim())
			.orElseThrow(() -> new NotFoundException("Usuário responsável pela atribuição não encontrado."));
		List<Sector> sectors = loadSectors(request.sectorIds());

		sectorMemberRepository.deleteByUserId(member.getId());

		for (Sector sector : sectors) {
			SectorMember sectorMember = new SectorMember();
			sectorMember.setSector(sector);
			sectorMember.setUser(member);
			sectorMember.setAssignedBy(assignedBy);
			sectorMemberRepository.save(sectorMember);
		}

		return listMembers();
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
}
