package com.helpdesk.helpdesk.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.SectorMember;
import com.helpdesk.helpdesk.domain.TeamMembershipNotification;
import com.helpdesk.helpdesk.domain.TeamMembershipNotificationType;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.profile.ProfileResponse;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorMemberRepository;
import com.helpdesk.helpdesk.repository.TeamMembershipNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final JdbcTemplate jdbcTemplate;
	private final RoleRepository roleRepository;
	private final SectorMemberRepository sectorMemberRepository;
	private final TeamMembershipNotificationRepository teamMembershipNotificationRepository;

	public ProfileService(
		UserRepository userRepository,
		UserMapper userMapper,
		JdbcTemplate jdbcTemplate,
		RoleRepository roleRepository,
		SectorMemberRepository sectorMemberRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository
	) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
		this.jdbcTemplate = jdbcTemplate;
		this.roleRepository = roleRepository;
		this.sectorMemberRepository = sectorMemberRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
	}

	@Transactional(readOnly = true)
	public ProfileResponse getByEmail(String email) {
		return userRepository.findByEmailIgnoreCase(email)
			.map(userMapper::toProfileResponse)
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));
	}

	@Transactional
	public void deleteByEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		jdbcTemplate.update("delete from team_invites where email = ?", normalizedEmail);
		jdbcTemplate.update("delete from ticket_messages where author_id = ?", user.getId());
		jdbcTemplate.update("delete from ticket_attachments where uploaded_by = ?", user.getId());
		jdbcTemplate.update("delete from ticket_status_history where changed_by = ?", user.getId());
		jdbcTemplate.update(
			"delete from tickets where requester_id = ? or sector_id in (select id from sectors where created_by = ?)",
			user.getId(),
			user.getId()
		);
		jdbcTemplate.update("delete from team_invites where invited_by = ?", user.getId());
		jdbcTemplate.update("delete from sector_members where assigned_by = ?", user.getId());
		jdbcTemplate.update("delete from sectors where created_by = ?", user.getId());

		userRepository.delete(user);
	}

	@Transactional
	public void deleteCompanyByEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		User admin = userRepository.findByEmailIgnoreCase(normalizedEmail)
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		if (!hasRole(admin, "ADMIN")) {
			throw new IllegalArgumentException("Somente administradores podem excluir a empresa.");
		}

		String companyName = resolveCompanyName(admin);
		Map<java.util.UUID, User> affectedMembersById = new LinkedHashMap<>();
		for (SectorMember membership : sectorMemberRepository.findBySectorCreatedByIdOrderByAssignedAtAsc(admin.getId())) {
			User member = membership.getUser();
			if (member == null || member.getId().equals(admin.getId())) {
				continue;
			}
			affectedMembersById.putIfAbsent(member.getId(), member);
		}

		if (!affectedMembersById.isEmpty()) {
			teamMembershipNotificationRepository.saveAll(
				affectedMembersById.values().stream()
					.map(member -> createCompanyDeletedNotification(member, admin, companyName))
					.toList()
			);
		}

		jdbcTemplate.update("delete from tickets where sector_id in (select id from sectors where created_by = ?)", admin.getId());
		jdbcTemplate.update("delete from team_invites where invited_by = ?", admin.getId());
		jdbcTemplate.update("delete from sectors where created_by = ?", admin.getId());

		updateAffectedMemberRoles(affectedMembersById.values());

		admin.getRoles().removeIf(role -> "ADMIN".equalsIgnoreCase(role.getCode()));
		ensureHasDefaultRole(admin);
		admin.setCompanyName(null);
		admin.setCompanyDocument(null);
		userRepository.save(admin);
	}

	private void updateAffectedMemberRoles(Collection<User> affectedMembers) {
		Role defaultUserRole = loadDefaultUserRole();

		for (User member : affectedMembers) {
			if (!sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).isEmpty()) {
				continue;
			}

			member.getRoles().removeIf(role -> "EMPLOYEE".equalsIgnoreCase(role.getCode()));
			if (member.getRoles().isEmpty()) {
				member.getRoles().add(defaultUserRole);
			}
			userRepository.save(member);
		}
	}

	private TeamMembershipNotification createCompanyDeletedNotification(User member, User removedBy, String companyName) {
		TeamMembershipNotification notification = new TeamMembershipNotification();
		notification.setRecipient(member);
		notification.setRemovedBy(removedBy);
		notification.setCompanyName(companyName);
		notification.setType(TeamMembershipNotificationType.COMPANY_DELETED);
		return notification;
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

	private String resolveCompanyName(User user) {
		if (user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
			return user.getCompanyName();
		}
		return user.getFullName();
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream().anyMatch(role -> role.getCode().equalsIgnoreCase(roleCode));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
