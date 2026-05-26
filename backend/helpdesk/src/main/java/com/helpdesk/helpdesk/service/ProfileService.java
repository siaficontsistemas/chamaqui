package com.helpdesk.helpdesk.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
import com.helpdesk.helpdesk.dto.profile.UpdateProfileRequest;
import com.helpdesk.helpdesk.repository.CompanyMembershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.SectorMemberRepository;
import com.helpdesk.helpdesk.repository.TeamMembershipNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class ProfileService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final JdbcTemplate jdbcTemplate;
	private final CompanyMembershipRepository companyMembershipRepository;
	private final RoleRepository roleRepository;
	private final SectorMemberRepository sectorMemberRepository;
	private final TeamMembershipNotificationRepository teamMembershipNotificationRepository;
	private final CnpjLookupService cnpjLookupService;
	private final EmailDomainValidationService emailDomainValidationService;

	public ProfileService(
		UserRepository userRepository,
		UserMapper userMapper,
		JdbcTemplate jdbcTemplate,
		CompanyMembershipRepository companyMembershipRepository,
		RoleRepository roleRepository,
		SectorMemberRepository sectorMemberRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository,
		CnpjLookupService cnpjLookupService,
		EmailDomainValidationService emailDomainValidationService
	) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
		this.jdbcTemplate = jdbcTemplate;
		this.companyMembershipRepository = companyMembershipRepository;
		this.roleRepository = roleRepository;
		this.sectorMemberRepository = sectorMemberRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
		this.cnpjLookupService = cnpjLookupService;
		this.emailDomainValidationService = emailDomainValidationService;
	}

	@Transactional(readOnly = true)
	public ProfileResponse getByEmail(String email) {
		return userRepository.findByEmailIgnoreCase(email)
			.map(userMapper::toProfileResponse)
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));
	}

	@Transactional
	public ProfileResponse update(UpdateProfileRequest request) {
		String normalizedCurrentEmail = normalizeEmail(request.currentEmail());
		User user = userRepository.findByEmailIgnoreCase(normalizedCurrentEmail)
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		String normalizedEmail = normalizeEmail(request.email());
		emailDomainValidationService.ensurePublicEmailDomainExists(normalizedEmail);
		User existingUserByEmail = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
		if (existingUserByEmail != null && !existingUserByEmail.getId().equals(user.getId())) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse email.");
		}

		user.setFullName(request.fullName().trim());
		user.setEmail(normalizedEmail);
		user.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));

		if (hasRole(user, "ADMIN")) {
			String companyName = blankToNull(request.companyName());
			String normalizedCompanyDocument = normalizeDocumentNumber(request.companyDocument());

			if (companyName == null) {
				throw new IllegalArgumentException("Informe o nome da empresa.");
			}
			if (normalizedCompanyDocument == null || normalizedCompanyDocument.length() != 14) {
				throw new IllegalArgumentException("Informe um CNPJ válido para a empresa.");
			}
			if (!normalizedCompanyDocument.equals(user.getCompanyDocument())
				&& userRepository.existsAdminCompanyByCompanyDocument(normalizedCompanyDocument)) {
				throw new IllegalArgumentException("Já existe uma conta cadastrada para esse CNPJ.");
			}

			cnpjLookupService.ensureCompanyExists(normalizedCompanyDocument);
			user.setCompanyName(companyName);
			user.setCompanyDocument(normalizedCompanyDocument);
		}

		return userMapper.toProfileResponse(userRepository.save(user));
	}

	@Transactional
	public void deleteByEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		if (isCompanyAdmin(user)) {
			deleteCompanyData(user, true);
		}

		deleteUserOwnedRecords(user.getId(), normalizedEmail);

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

		deleteCompanyData(admin, false);
	}

	private void deleteCompanyData(User admin, boolean deletingAdminAccount) {
		String companyName = resolveCompanyName(admin);
		Map<UUID, User> affectedMembersById = new LinkedHashMap<>();

		for (com.helpdesk.helpdesk.domain.CompanyMembership membership :
			companyMembershipRepository.findByCompanyOwnerIdOrderByJoinedAtAsc(admin.getId())) {
			User member = membership.getUser();
			if (member == null || member.getId() == null) {
				continue;
			}
			affectedMembersById.putIfAbsent(member.getId(), member);
		}

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
					.map(member -> createCompanyDeletedNotification(
						member,
						deletingAdminAccount ? member : admin,
						companyName
					))
					.toList()
			);
		}

		deleteCompanyOwnedRecords(admin.getId());
		companyMembershipRepository.deleteByCompanyOwnerId(admin.getId());
		jdbcTemplate.update("update users set company_owner_id = null where company_owner_id = ? and id <> ?", admin.getId(), admin.getId());
		refreshAffectedPrimaryCompanyOwners(affectedMembersById.values());

		updateAffectedMemberRoles(affectedMembersById.values());

		if (!deletingAdminAccount) {
			admin.getRoles().removeIf(role -> "ADMIN".equalsIgnoreCase(role.getCode()));
			ensureHasDefaultRole(admin);
			admin.setCompanyName(null);
			admin.setCompanyDocument(null);
			admin.setCompanyType(null);
			userRepository.save(admin);
		}
	}

	private void deleteUserOwnedRecords(UUID userId, String normalizedEmail) {
		jdbcTemplate.update("delete from team_invites where email = ?", normalizedEmail);
		jdbcTemplate.update(
			"""
			delete from whatsapp_conversations
			where company_owner_id = ?
				or sector_id in (select id from sectors where created_by = ?)
				or active_ticket_id in (
					select id
					from tickets
					where requester_id = ?
					   or sector_id in (select id from sectors where created_by = ?)
				)
			""",
			userId,
			userId,
			userId,
			userId
		);
		jdbcTemplate.update(
			"""
			delete from company_partnership_notifications
			where recipient_id = ?
				or actor_user_id = ?
				or requester_company_id = ?
				or target_company_id = ?
				or company_partnership_id in (
					select id
					from company_partnerships
					where requester_company_id = ?
					   or target_company_id = ?
					   or requested_by_user_id = ?
					   or responded_by_user_id = ?
				)
			""",
			userId,
			userId,
			userId,
			userId,
			userId,
			userId,
			userId,
			userId
		);
		jdbcTemplate.update(
			"""
			delete from company_partnerships
			where requester_company_id = ?
				or target_company_id = ?
				or requested_by_user_id = ?
				or responded_by_user_id = ?
			""",
			userId,
			userId,
			userId,
			userId
		);
		jdbcTemplate.update(
			"""
			delete from company_access_requests
			where requester_user_id = ?
				or target_company_id = ?
			""",
			userId,
			userId
		);
		jdbcTemplate.update("delete from ticket_messages where author_id = ?", userId);
		jdbcTemplate.update("delete from ticket_attachments where uploaded_by = ?", userId);
		jdbcTemplate.update("delete from ticket_status_history where changed_by = ?", userId);
		jdbcTemplate.update(
			"delete from tickets where requester_id = ? or sector_id in (select id from sectors where created_by = ?)",
			userId,
			userId
		);
		jdbcTemplate.update("delete from team_invites where invited_by = ?", userId);
		jdbcTemplate.update("delete from sector_members where assigned_by = ?", userId);
		jdbcTemplate.update("delete from sectors where created_by = ?", userId);
		jdbcTemplate.update("update users set company_owner_id = null where company_owner_id = ? and id <> ?", userId, userId);
	}

	private void deleteCompanyOwnedRecords(UUID adminId) {
		jdbcTemplate.update(
			"""
			delete from whatsapp_conversations
			where company_owner_id = ?
				or sector_id in (select id from sectors where created_by = ?)
				or active_ticket_id in (
					select id
					from tickets
					where sector_id in (select id from sectors where created_by = ?)
				)
			""",
			adminId,
			adminId,
			adminId
		);
		jdbcTemplate.update(
			"""
			delete from company_partnership_notifications
			where requester_company_id = ?
				or target_company_id = ?
				or recipient_id = ?
				or company_partnership_id in (
					select id
					from company_partnerships
					where requester_company_id = ?
					   or target_company_id = ?
				)
			""",
			adminId,
			adminId,
			adminId,
			adminId,
			adminId
		);
		jdbcTemplate.update(
			"""
			delete from company_partnerships
			where requester_company_id = ?
				or target_company_id = ?
			""",
			adminId,
			adminId
		);
		jdbcTemplate.update("delete from company_access_requests where target_company_id = ?", adminId);
		jdbcTemplate.update("delete from tickets where sector_id in (select id from sectors where created_by = ?)", adminId);
		jdbcTemplate.update("delete from team_invites where invited_by = ?", adminId);
		jdbcTemplate.update("delete from sectors where created_by = ?", adminId);
	}

	private void updateAffectedMemberRoles(Collection<User> affectedMembers) {
		Role defaultUserRole = loadDefaultUserRole();

		for (User member : affectedMembers) {
			if (!sectorMemberRepository.findByUserIdOrderByAssignedAtAsc(member.getId()).isEmpty()) {
				continue;
			}
			if (companyMembershipRepository.existsByUserIdAndCompanyType(
				member.getId(),
				com.helpdesk.helpdesk.domain.CompanyType.RESPONDER
			)) {
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

	private boolean isCompanyAdmin(User user) {
		return hasRole(user, "ADMIN")
			&& (
				(user.getCompanyName() != null && !user.getCompanyName().isBlank())
				|| (user.getCompanyDocument() != null && !user.getCompanyDocument().isBlank())
				|| user.getCompanyType() != null
				|| !companyMembershipRepository.findByCompanyOwnerIdOrderByJoinedAtAsc(user.getId()).isEmpty()
			);
	}

	private void refreshAffectedPrimaryCompanyOwners(Collection<User> affectedMembers) {
		for (User member : affectedMembers) {
			User currentCompanyOwner = member.getCompanyOwner();
			if (currentCompanyOwner != null
				&& companyMembershipRepository.existsByUserIdAndCompanyOwnerId(member.getId(), currentCompanyOwner.getId())) {
				continue;
			}

			User nextCompanyOwner = companyMembershipRepository.findByUserIdOrderByJoinedAtAsc(member.getId()).stream()
				.map(com.helpdesk.helpdesk.domain.CompanyMembership::getCompanyOwner)
				.findFirst()
				.orElse(null);
			member.setCompanyOwner(nextCompanyOwner);
			userRepository.save(member);
		}
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream().anyMatch(role -> role.getCode().equalsIgnoreCase(roleCode));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeDocumentNumber(String value) {
		String normalizedValue = blankToNull(value);
		if (normalizedValue == null) {
			return null;
		}
		return normalizedValue.replaceAll("\\D", "");
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String normalizePhoneNumber(String value) {
		String normalizedValue = blankToNull(value);
		if (normalizedValue == null) {
			return null;
		}

		String digits = normalizedValue.replaceAll("\\D", "");
		if (digits.startsWith("55") && digits.length() == 13) {
			digits = digits.substring(2);
		}

		return digits.isBlank() ? null : digits;
	}
}
