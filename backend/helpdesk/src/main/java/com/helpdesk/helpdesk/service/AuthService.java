package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.LegalDocumentType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.helpdesk.dto.auth.LoginRequest;
import com.helpdesk.helpdesk.dto.auth.RegisterInviteResponse;
import com.helpdesk.helpdesk.dto.auth.RegisterRequest;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.util.BrazilianDocumentValidator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;
	private final CompanyAccessRequestService companyAccessRequestService;
	private final CnpjLookupService cnpjLookupService;
	private final EmailDomainValidationService emailDomainValidationService;
	private final CompanyProvisioningService companyProvisioningService;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;
	private final CompanyPartnershipRepository companyPartnershipRepository;
	private final AppSessionService appSessionService;
	private final LegalDocumentService legalDocumentService;
	private final LegalAcceptanceService legalAcceptanceService;

	public AuthService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		UserMapper userMapper,
		CompanyAccessRequestService companyAccessRequestService,
		CnpjLookupService cnpjLookupService,
		EmailDomainValidationService emailDomainValidationService,
		CompanyProvisioningService companyProvisioningService,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService,
		CompanyPartnershipRepository companyPartnershipRepository,
		AppSessionService appSessionService,
		LegalDocumentService legalDocumentService,
		LegalAcceptanceService legalAcceptanceService
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.userMapper = userMapper;
		this.companyAccessRequestService = companyAccessRequestService;
		this.cnpjLookupService = cnpjLookupService;
		this.emailDomainValidationService = emailDomainValidationService;
		this.companyProvisioningService = companyProvisioningService;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
		this.companyPartnershipRepository = companyPartnershipRepository;
		this.appSessionService = appSessionService;
		this.legalDocumentService = legalDocumentService;
		this.legalAcceptanceService = legalAcceptanceService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request, HttpSession session, HttpServletRequest httpRequest) {
		boolean isAdminRegistration = "admin".equalsIgnoreCase(request.role());
		CompanyType companyType = CompanyType.fromValue(request.companyType());
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		emailDomainValidationService.ensurePublicEmailDomainExists(normalizedEmail);
		String normalizedDocumentNumber = normalizeDocumentNumber(request.documentNumber());
		String normalizedInviteToken = blankToNull(request.inviteToken());
		String companyName = blankToNull(request.companyName());
		String normalizedCompanyDocument = normalizeCompanyDocument(request.companyDocument());
		User companyOwner = null;
		User approvalTargetCompany = null;
		User tenantCompany = tenantAccessService.hasCurrentTenant() ? tenantAccessService.loadCurrentTenantOwner() : null;
		boolean directTenantMembership = false;
		User existingUserByEmail = scopedUserLookupService.findUniqueByEmailForRegistrationScope(normalizedEmail)
			.orElse(null);
		List<User> existingUsersByDocument = findUsersByDocument(normalizedDocumentNumber);
		User upgradeableUser = resolveUpgradeableUser(existingUserByEmail, existingUsersByDocument);

		if (upgradeableUser == null && existingUserByEmail != null) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse email.");
		}
		if (!isAdminRegistration && upgradeableUser == null && !existingUsersByDocument.isEmpty()) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse CPF/documento.");
		}
		if (isAdminRegistration && companyName == null) {
			throw new IllegalArgumentException("Informe o nome da empresa para cadastrar um administrador.");
		}
		if (isAdminRegistration && normalizedCompanyDocument == null) {
			throw new IllegalArgumentException("Informe o CNPJ da empresa para cadastrar um administrador.");
		}
		if (isAdminRegistration && companyType == null) {
			throw new IllegalArgumentException("Selecione o tipo da empresa para cadastrar um administrador.");
		}
		if (!isAdminRegistration && companyType == null) {
			throw new IllegalArgumentException("Selecione primeiro se o usuário vai criar ou responder chamados.");
		}
		if (normalizedDocumentNumber == null || !BrazilianDocumentValidator.isValidCpf(normalizedDocumentNumber)) {
			throw new IllegalArgumentException("Informe um CPF válido.");
		}
		if (normalizedCompanyDocument != null && normalizedCompanyDocument.length() != 14) {
			throw new IllegalArgumentException("Informe um CNPJ válido para a empresa.");
		}
		if (normalizedCompanyDocument != null && userRepository.existsAdminCompanyByCompanyDocument(normalizedCompanyDocument)) {
			throw new IllegalArgumentException("Já existe uma conta cadastrada para esse CNPJ.");
		}
		if (isAdminRegistration) {
			cnpjLookupService.ensureCompanyExists(normalizedCompanyDocument);
		}
		if (tenantCompany != null) {
			if (isAdminRegistration) {
				throw new IllegalArgumentException(
					"Não é permitido cadastrar um novo administrador dentro do subdomínio de uma empresa."
				);
			}
			if (normalizedInviteToken != null) {
				companyAccessRequestService.ensureInviteMatchesCurrentTenant(normalizedInviteToken);
			}

			if (companyType == tenantCompany.getCompanyType()) {
				if (request.companyOwnerId() != null && !tenantCompany.getId().equals(request.companyOwnerId())) {
					throw new IllegalArgumentException("Esse cadastro não pode ser vinculado a outra empresa.");
				}
				companyOwner = tenantCompany;
				directTenantMembership = tenantCompany.getCompanyType() == CompanyType.REQUESTER;
			} else if (
				tenantCompany.getCompanyType() == CompanyType.RESPONDER
					&& companyType == CompanyType.REQUESTER
			) {
				if (request.companyOwnerId() == null) {
					throw new IllegalArgumentException("Selecione a empresa cliente para concluir esse cadastro.");
				}

				companyOwner = userRepository.findAdminCompanyOwnerByIdAndCompanyType(
					request.companyOwnerId(),
					CompanyType.REQUESTER
				).orElseThrow(() -> new NotFoundException("Empresa cliente não encontrada."));

				boolean hasAcceptedPartnership = companyPartnershipRepository.existsByCompanyPairAndStatus(
					tenantCompany.getId(),
					companyOwner.getId(),
					CompanyPartnershipStatus.ACCEPTED
				);
				if (!hasAcceptedPartnership) {
					throw new IllegalArgumentException(
						"A empresa cliente selecionada não está vinculada à empresa provedora atual."
					);
				}
				approvalTargetCompany = tenantCompany;

			} else {
				throw new IllegalArgumentException("Esse subdomínio só permite os tipos de cadastro disponíveis para a empresa atual.");
			}
		}
		if (!isAdminRegistration && request.companyOwnerId() != null && normalizedInviteToken == null) {
			if (tenantCompany == null) {
				companyOwner = userRepository.findStandaloneAdminCompanyOwnerByIdAndCompanyType(
					request.companyOwnerId(),
					companyType
				)
					.orElseThrow(() -> new NotFoundException("Empresa não encontrada para o tipo selecionado."));
			}
		}

		Role role = roleRepository.findByCode(request.role().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		User user = upgradeableUser == null ? new User() : upgradeableUser;
		user.setFullName(request.fullName().trim());
		user.setEmail(normalizedEmail);
		user.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));
		user.setDocumentNumber(normalizedDocumentNumber);
		user.setCompanyName(isAdminRegistration ? companyName : null);
		user.setCompanyDocument(isAdminRegistration ? normalizedCompanyDocument : null);
		user.setCompanyType(isAdminRegistration ? companyType : null);
		user.setCompanyOwner(isAdminRegistration ? null : companyOwner);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setStatus(resolveInitialStatus(isAdminRegistration, normalizedInviteToken, companyOwner, directTenantMembership));
		user.setEmailVerified(true);
		user.setSimplified(false);
		user.setTermsAcceptedAt(OffsetDateTime.now());
		user.setTermsVersion(legalDocumentService.getCurrentVersion(LegalDocumentType.TERMS_OF_USE));
		user.setPrivacyPolicyAcceptedAt(OffsetDateTime.now());
		user.setPrivacyPolicyVersion(legalDocumentService.getCurrentVersion(LegalDocumentType.PRIVACY_POLICY));
		user.getRoles().clear();
		user.getRoles().add(role);

		User savedUser = userRepository.save(user);
		legalAcceptanceService.recordRegistrationAcceptances(savedUser, httpRequest);
		if (isAdminRegistration) {
			companyProvisioningService.syncAdminCompany(savedUser);
		}

		if (!isAdminRegistration) {
			if (normalizedInviteToken != null) {
				companyAccessRequestService.acceptAdminInviteDuringRegistration(savedUser, normalizedInviteToken);
			} else if (directTenantMembership && companyOwner != null) {
				companyAccessRequestService.attachApprovedUserToCompany(savedUser, companyOwner);
			} else if (approvalTargetCompany != null) {
				companyAccessRequestService.createPendingRequest(savedUser, approvalTargetCompany);
			} else if (companyOwner != null) {
				companyAccessRequestService.createPendingRequest(savedUser, companyOwner);
			} else {
				companyAccessRequestService.attachPendingAdminInvitesToRegisteredUser(savedUser);
			}
		}

		if (savedUser.getStatus() == UserStatus.ACTIVE) {
			return appSessionService.login(savedUser, session);
		}
		return userMapper.toAuthResponse(savedUser);
	}

	@Transactional(readOnly = true)
	public RegisterInviteResponse getRegisterInvite(String inviteToken) {
		return companyAccessRequestService.getRegisterInvite(inviteToken);
	}

	@Transactional
	public AuthResponse login(LoginRequest request, HttpSession session) {
		User user = scopedUserLookupService.resolveLoginCandidate(request.email().trim())
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Email ou senha inválidos.");
		}
		tenantAccessService.ensureMainHostLoginAllowed(user);
		if (!tenantAccessService.belongsToCurrentTenant(user)) {
			throw new IllegalArgumentException("Email ou senha inválidos.");
		}

		if (user.getStatus() == UserStatus.PENDING) {
			throw new IllegalArgumentException(
				"Seu cadastro ainda está aguardando aprovação do administrador da empresa."
			);
		}

		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new IllegalArgumentException("O usuário não está ativo para acessar o sistema.");
		}

		return appSessionService.login(user, session);
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String normalizeCompanyDocument(String value) {
		String trimmedValue = blankToNull(value);
		if (trimmedValue == null) {
			return null;
		}
		return trimmedValue.replaceAll("\\D", "");
	}

	private String normalizeDocumentNumber(String value) {
		String trimmedValue = blankToNull(value);
		if (trimmedValue == null) {
			return null;
		}
		return trimmedValue.replaceAll("\\D", "");
	}

	private String normalizePhoneNumber(String value) {
		String trimmedValue = blankToNull(value);
		if (trimmedValue == null) {
			return null;
		}

		String digits = trimmedValue.replaceAll("\\D", "");
		if (digits.startsWith("55") && digits.length() == 13) {
			digits = digits.substring(2);
		}

		return digits.isBlank() ? null : digits;
	}

	private List<User> findUsersByDocument(String normalizedDocumentNumber) {
		if (normalizedDocumentNumber == null) {
			return List.of();
		}
		if (tenantAccessService.hasCurrentTenant()) {
			return scopedUserLookupService.findAllByDocumentInCurrentTenant(normalizedDocumentNumber);
		}
		return scopedUserLookupService.findStandaloneUsersByDocument(normalizedDocumentNumber);
	}

	private User resolveUpgradeableUser(User existingUserByEmail, List<User> existingUsersByDocument) {
		if (existingUserByEmail == null && existingUsersByDocument.isEmpty()) {
			return null;
		}
		if (existingUserByEmail != null
			&& existingUsersByDocument.stream()
				.noneMatch(existingUserByDocument -> existingUserByDocument.getId().equals(existingUserByEmail.getId()))
			&& !existingUsersByDocument.isEmpty()) {
			throw new IllegalArgumentException(
				"Já existem cadastros conflitantes para esse email e CPF. Revise os dados informados."
			);
		}

		if (existingUserByEmail != null) {
			return isReusableRegistrationCandidate(existingUserByEmail) ? existingUserByEmail : null;
		}

		if (existingUsersByDocument.size() != 1) {
			return null;
		}

		User candidate = existingUsersByDocument.getFirst();
		return candidate.isSimplified() || isReusableRegistrationCandidate(candidate) ? candidate : null;
	}

	private boolean isReusableRegistrationCandidate(User candidate) {
		return candidate.getStatus() != UserStatus.ACTIVE
			&& candidate.getCompanyOwner() == null
			&& blankToNull(candidate.getCompanyName()) == null
			&& blankToNull(candidate.getCompanyDocument()) == null;
	}

	private UserStatus resolveInitialStatus(
		boolean isAdminRegistration,
		String inviteToken,
		User companyOwner,
		boolean directTenantMembership
	) {
		if (isAdminRegistration || inviteToken != null || companyOwner == null || directTenantMembership) {
			return UserStatus.ACTIVE;
		}

		return UserStatus.PENDING;
	}

}
