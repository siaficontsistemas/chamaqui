package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientRequest;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientResponse;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class ClientPreRegistrationService {
	private static final String SESSION_USER_EMAIL = "APP_USER_EMAIL";
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final TenantAccessService tenantAccessService;
	private final CompanyPartnershipRepository partnershipRepository;
	private final AppSessionService appSessionService;

	public ClientPreRegistrationService(UserRepository userRepository, RoleRepository roleRepository,
		PasswordEncoder passwordEncoder, TenantAccessService tenantAccessService,
		CompanyPartnershipRepository partnershipRepository, AppSessionService appSessionService) {
		this.userRepository = userRepository; this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder; this.tenantAccessService = tenantAccessService;
		this.partnershipRepository = partnershipRepository; this.appSessionService = appSessionService;
	}

	@Transactional
	public PreRegisterClientResponse create(PreRegisterClientRequest request, HttpSession session) {
		User actor = appSessionService.requireUser(session);
		if (!hasRole(actor, "ADMIN") && !hasRole(actor, "EMPLOYEE")) {
			throw new IllegalArgumentException("Somente funcionários e administradores podem criar pré-cadastros.");
		}
		String phone = normalizePhone(request.phoneNumber());
		if (phone == null || phone.length() < 10) throw new IllegalArgumentException("Informe um telefone válido.");
		if (userRepository.existsByPhoneNumber(phone)) throw new IllegalArgumentException("Já existe um cadastro associado a esse telefone.");
		User company = userRepository.findAdminCompanyOwnerByIdAndCompanyType(request.companyOwnerId(), CompanyType.REQUESTER)
			.orElseThrow(() -> new NotFoundException("Empresa cliente não encontrada."));
		if (!tenantAccessService.hasCurrentTenant() || !partnershipRepository.existsByCompanyPairAndStatus(
			tenantAccessService.requireCurrentTenantOwnerUserId(), company.getId(), CompanyPartnershipStatus.ACCEPTED)) {
			throw new IllegalArgumentException("A empresa cliente não está vinculada à empresa atual.");
		}
		Role role = roleRepository.findByCode("USER").orElseThrow(() -> new NotFoundException("Perfil de cliente não encontrado."));
		User user = new User();
		user.setFullName(request.fullName().trim()); user.setPhoneNumber(phone); user.setCompanyOwner(company);
		user.setEmail("pre-cadastro-" + UUID.randomUUID() + "@invalid.chamaqui");
		user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); user.setStatus(com.helpdesk.helpdesk.domain.UserStatus.ACTIVE);
		user.setEmailVerified(false); user.setPreRegistered(true); user.setRegistrationTokenHash(null);
		user.setRegistrationTokenExpiresAt(null); user.getRoles().add(role);
		User saved = userRepository.save(user);
		return new PreRegisterClientResponse(saved.getId(), saved.getFullName(), saved.getEmail(), saved.getPhoneNumber(), company.getCompanyName());
	}

	private boolean hasRole(User user, String role) { return user.getRoles().stream().anyMatch(item -> role.equalsIgnoreCase(item.getCode())); }
	private String normalizePhone(String value) { String digits = value == null ? "" : value.replaceAll("\\D", ""); return digits.startsWith("55") && digits.length() == 13 ? digits.substring(2) : (digits.isBlank() ? null : digits); }
}
