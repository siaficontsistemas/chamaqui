package com.helpdesk.helpdesk.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientRequest;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientResponse;
import com.helpdesk.helpdesk.dto.client.UpdatePreRegisterClientRequest;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class ClientPreRegistrationService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final TenantAccessService tenantAccessService;
	private final CompanyPartnershipRepository partnershipRepository;
	private final TicketRepository ticketRepository;
	private final AppSessionService appSessionService;

	public ClientPreRegistrationService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		TenantAccessService tenantAccessService,
		CompanyPartnershipRepository partnershipRepository,
		TicketRepository ticketRepository,
		AppSessionService appSessionService
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.tenantAccessService = tenantAccessService;
		this.partnershipRepository = partnershipRepository;
		this.ticketRepository = ticketRepository;
		this.appSessionService = appSessionService;
	}

	@Transactional
	public PreRegisterClientResponse create(PreRegisterClientRequest request, HttpSession session) {
		User actor = requireStaff(session);
		String phone = normalizeAndValidatePhone(request.phoneNumber());
		if (userRepository.existsByPhoneNumber(phone)) {
			throw new IllegalArgumentException("Já existe um cadastro associado a esse telefone.");
		}
		User company = loadAllowedClientCompany(request.companyOwnerId());
		Role role = roleRepository.findByCode("USER")
			.orElseThrow(() -> new NotFoundException("Perfil de cliente não encontrado."));

		User user = new User();
		user.setFullName(request.fullName().trim());
		user.setPhoneNumber(phone);
		user.setCompanyOwner(company);
		user.setEmail("pre-cadastro-" + UUID.randomUUID() + "@invalid.chamaqui");
		user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerified(false);
		user.setPreRegistered(true);
		user.setRegistrationTokenHash(null);
		user.setRegistrationTokenExpiresAt(null);
		user.getRoles().add(role);

		return toResponse(userRepository.save(user));
	}

	@Transactional
	public PreRegisterClientResponse update(UUID clientId, UpdatePreRegisterClientRequest request, HttpSession session) {
		requireStaff(session);
		User user = loadManagedPreRegistration(clientId);
		String phone = normalizeAndValidatePhone(request.phoneNumber());
		if (userRepository.existsByPhoneNumberAndIdNot(phone, clientId)) {
			throw new IllegalArgumentException("Já existe outro cadastro associado a esse telefone.");
		}

		User company = loadAllowedClientCompany(request.companyOwnerId());
		user.setFullName(request.fullName().trim());
		user.setPhoneNumber(phone);
		user.setCompanyOwner(company);
		return toResponse(userRepository.save(user));
	}

	@Transactional
	public void delete(UUID clientId, HttpSession session) {
		requireStaff(session);
		User user = loadManagedPreRegistration(clientId);
		if (ticketRepository.existsByRequesterId(clientId)) {
			throw new IllegalArgumentException(
				"Este pré-cadastro possui chamados associados e não pode ser excluído para preservar o histórico."
			);
		}
		user.getRoles().clear();
		userRepository.delete(user);
	}

	private User requireStaff(HttpSession session) {
		User actor = appSessionService.requireUser(session);
		if (!hasRole(actor, "ADMIN") && !hasRole(actor, "EMPLOYEE")) {
			throw new IllegalArgumentException("Somente funcionários e administradores podem gerenciar pré-cadastros.");
		}
		if (!tenantAccessService.hasCurrentTenant()) {
			throw new IllegalArgumentException("O pré-cadastro precisa ser gerenciado dentro do subdomínio da empresa.");
		}
		tenantAccessService.ensureUserBelongsToCurrentTenant(actor, "Esse usuário não pertence ao tenant atual.");
		return actor;
	}

	private User loadManagedPreRegistration(UUID clientId) {
		User user = userRepository.findById(clientId)
			.orElseThrow(() -> new NotFoundException("Pré-cadastro não encontrado."));
		if (!user.isPreRegistered()) {
			throw new IllegalArgumentException("Este cliente já concluiu o cadastro e não é mais um pré-cadastro.");
		}
		if (user.getCompanyOwner() == null || !isCompanyAllowed(user.getCompanyOwner().getId())) {
			throw new IllegalArgumentException("Você não tem acesso a este pré-cadastro.");
		}
		return user;
	}

	private User loadAllowedClientCompany(UUID companyOwnerId) {
		User company = userRepository.findAdminCompanyOwnerByIdAndCompanyType(companyOwnerId, CompanyType.REQUESTER)
			.orElseThrow(() -> new NotFoundException("Empresa cliente não encontrada."));
		if (!isCompanyAllowed(company.getId())) {
			throw new IllegalArgumentException("A empresa cliente não está vinculada à empresa atual.");
		}
		return company;
	}

	private boolean isCompanyAllowed(UUID companyId) {
		return tenantAccessService.hasCurrentTenant()
			&& partnershipRepository.existsByCompanyPairAndStatus(
				tenantAccessService.requireCurrentTenantOwnerUserId(),
				companyId,
				CompanyPartnershipStatus.ACCEPTED
			);
	}

	private PreRegisterClientResponse toResponse(User user) {
		User company = user.getCompanyOwner();
		return new PreRegisterClientResponse(
			user.getId(),
			user.getFullName(),
			user.getEmail(),
			user.getPhoneNumber(),
			company == null ? null : company.getCompanyName(),
			company == null ? null : company.getId(),
			user.isPreRegistered()
		);
	}

	private boolean hasRole(User user, String role) {
		return user.getRoles().stream().anyMatch(item -> role.equalsIgnoreCase(item.getCode()));
	}

	private String normalizeAndValidatePhone(String value) {
		String digits = value == null ? "" : value.replaceAll("\\D", "");
		String normalized = digits.startsWith("55") && digits.length() == 13 ? digits.substring(2) : digits;
		if (normalized.isBlank() || normalized.length() < 10) {
			throw new IllegalArgumentException("Informe um telefone válido.");
		}
		return normalized;
	}
}
