package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Company;
import com.helpdesk.helpdesk.domain.CompanyPartnership;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.company.ClientCompanyLookupResponse;
import com.helpdesk.helpdesk.dto.company.ClientCompanyRegistrationResponse;
import com.helpdesk.helpdesk.dto.company.CreateClientCompanyRequest;
import com.helpdesk.helpdesk.dto.company.LinkExistingClientCompanyRequest;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class ClientCompanyRegistrationService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;
	private final CompanyPartnershipRepository companyPartnershipRepository;
	private final CnpjLookupService cnpjLookupService;
	private final EmailDomainValidationService emailDomainValidationService;

	public ClientCompanyRegistrationService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService,
		CompanyPartnershipRepository companyPartnershipRepository,
		CnpjLookupService cnpjLookupService,
		EmailDomainValidationService emailDomainValidationService
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
		this.companyPartnershipRepository = companyPartnershipRepository;
		this.cnpjLookupService = cnpjLookupService;
		this.emailDomainValidationService = emailDomainValidationService;
	}

	@Transactional
	public ClientCompanyRegistrationResponse register(CreateClientCompanyRequest request) {
		User providerAdmin = loadProviderAdmin(request.createdByEmail());
		String normalizedCompanyDocument = normalizeCompanyDocument(request.companyDocument());
		String normalizedCompanyContactEmail = normalizeOptionalCompanyEmail(request.companyEmail());
		String normalizedCompanyContactPhone = normalizePhoneNumber(request.companyPhoneNumber());

		if (normalizedCompanyContactEmail != null) {
			emailDomainValidationService.ensurePublicEmailDomainExists(normalizedCompanyContactEmail);
		}
		cnpjLookupService.ensureCompanyExists(normalizedCompanyDocument);

		User existingCompany = findTenantScopedAdminCompanyByDocument(normalizedCompanyDocument);
		if (existingCompany != null) {
			throw new IllegalArgumentException("Já existe uma empresa cliente cadastrada com esse CNPJ.");
		}

		Role adminRole = roleRepository.findByCode("ADMIN")
			.orElseThrow(() -> new NotFoundException("Perfil de administrador não encontrado."));

		User clientCompanyOwner = new User();
		clientCompanyOwner.setFullName(request.companyName().trim());
		clientCompanyOwner.setEmail(generateInternalCompanyEmail(providerAdmin, normalizedCompanyDocument));
		clientCompanyOwner.setPhoneNumber(null);
		clientCompanyOwner.setDocumentNumber(null);
		clientCompanyOwner.setCompanyName(request.companyName().trim());
		clientCompanyOwner.setCompanyDocument(normalizedCompanyDocument);
		clientCompanyOwner.setCompanyContactEmail(normalizedCompanyContactEmail);
		clientCompanyOwner.setCompanyContactPhone(normalizedCompanyContactPhone);
		clientCompanyOwner.setCompanyType(CompanyType.REQUESTER);
		clientCompanyOwner.setCompanyOwner(providerAdmin);
		clientCompanyOwner.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		clientCompanyOwner.setStatus(UserStatus.PENDING);
		clientCompanyOwner.setEmailVerified(false);
		clientCompanyOwner.setSimplified(true);
		clientCompanyOwner.getRoles().clear();
		clientCompanyOwner.getRoles().add(adminRole);

		User savedClientCompanyOwner = userRepository.save(clientCompanyOwner);
		ensureAcceptedPartnership(providerAdmin, savedClientCompanyOwner);
		return toResponse(savedClientCompanyOwner, providerAdmin);
	}

	@Transactional(readOnly = true)
	public ClientCompanyLookupResponse lookup(String companyDocument, String createdByEmail) {
		User providerAdmin = loadProviderAdmin(createdByEmail);
		String normalizedCompanyDocument = normalizeCompanyDocument(companyDocument);
		User existingCompany = findTenantScopedAdminCompanyByDocument(normalizedCompanyDocument);

		if (existingCompany == null) {
			return new ClientCompanyLookupResponse("AVAILABLE", null, null, null, null);
		}

		if (existingCompany.getId().equals(providerAdmin.getId()) || existingCompany.getCompanyType() != CompanyType.REQUESTER) {
			return new ClientCompanyLookupResponse(
				"UNAVAILABLE",
				"Esse CNPJ já está vinculado a um cadastro que não pode ser usado como empresa cliente.",
				existingCompany.getId(),
				existingCompany.getCompanyName(),
				existingCompany.getCompanyDocument()
			);
		}

		CompanyPartnership partnership = findExistingPartnership(providerAdmin, existingCompany);
		if (partnership != null) {
			String status = partnership.getStatus() == CompanyPartnershipStatus.ACCEPTED ? "ALREADY_CLIENT" : "PENDING_LINK";
			String message = partnership.getStatus() == CompanyPartnershipStatus.ACCEPTED
				? "A empresa desse CNPJ já está vinculada à sua operação."
				: "Já existe um cadastro pendente para essa empresa cliente.";
			return new ClientCompanyLookupResponse(
				status,
				message,
				existingCompany.getId(),
				existingCompany.getCompanyName(),
				existingCompany.getCompanyDocument()
			);
		}

		return new ClientCompanyLookupResponse(
			"UNAVAILABLE",
			"Esse CNPJ já está cadastrado para uma empresa cliente deste domínio.",
			existingCompany.getId(),
			existingCompany.getCompanyName(),
			existingCompany.getCompanyDocument()
		);
	}

	@Transactional
	public ClientCompanyRegistrationResponse linkExisting(LinkExistingClientCompanyRequest request) {
		loadProviderAdmin(request.createdByEmail());
		throw new IllegalArgumentException(
			"Esse fluxo não está mais disponível. Cadastre a empresa cliente diretamente por esta tela."
		);
	}

	private User loadProviderAdmin(String email) {
		if (!tenantAccessService.hasCurrentTenant()) {
			throw new IllegalArgumentException("Esse cadastro só pode ser feito dentro do subdomínio da empresa provedora.");
		}

		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Administrador responsável não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");

		if (!hasRole(user, "ADMIN") || user.getCompanyType() != CompanyType.RESPONDER) {
			throw new IllegalArgumentException("Somente o administrador da empresa provedora pode cadastrar empresas clientes.");
		}

		return user;
	}

	private User findTenantScopedAdminCompanyByDocument(String companyDocument) {
		return userRepository.findAdminCompaniesByCompanyDocument(companyDocument).stream()
			.filter(tenantAccessService::belongsToCurrentTenant)
			.findFirst()
			.orElse(null);
	}

	private CompanyPartnership findExistingPartnership(User providerAdmin, User clientCompany) {
		return companyPartnershipRepository.findByCompanyPairAndStatuses(
			providerAdmin.getId(),
			clientCompany.getId(),
			EnumSet.of(CompanyPartnershipStatus.PENDING, CompanyPartnershipStatus.ACCEPTED)
		).stream().findFirst().orElse(null);
	}

	private void ensureAcceptedPartnership(User providerAdmin, User clientCompany) {
		CompanyPartnership partnership = new CompanyPartnership();
		partnership.setRequesterCompany(clientCompany);
		partnership.setTargetCompany(providerAdmin);
		partnership.setRequestedBy(providerAdmin);
		partnership.setRespondedBy(providerAdmin);
		partnership.setStatus(CompanyPartnershipStatus.ACCEPTED);
		partnership.setCreatedAt(OffsetDateTime.now());
		partnership.setRespondedAt(OffsetDateTime.now());
		companyPartnershipRepository.save(partnership);
	}

	private ClientCompanyRegistrationResponse toResponse(User clientCompanyOwner, User providerAdmin) {
		String subdomain = tenantAccessService.findPrimaryCompanyForUser(providerAdmin)
			.map(Company::getSubdomain)
			.orElse(null);

		return new ClientCompanyRegistrationResponse(
			clientCompanyOwner.getId(),
			clientCompanyOwner.getCompanyName(),
			clientCompanyOwner.getCompanyDocument(),
			clientCompanyOwner.getCompanyContactEmail(),
			clientCompanyOwner.getCompanyContactPhone(),
			subdomain
		);
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream()
			.anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe o email do administrador.");
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeOptionalCompanyEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String generateInternalCompanyEmail(User providerAdmin, String companyDocument) {
		return "client-company-" + companyDocument + "-" + providerAdmin.getId() + "@internal.chamaqui.local";
	}

	private String normalizeCompanyDocument(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Informe o CNPJ da empresa cliente.");
		}

		String normalizedValue = value.trim().replaceAll("\\D", "");
		if (normalizedValue.length() != 14) {
			throw new IllegalArgumentException("Informe um CNPJ válido para a empresa cliente.");
		}
		return normalizedValue;
	}

	private String normalizePhoneNumber(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalizedValue = value.trim();
		return normalizedValue.isBlank() ? null : normalizedValue;
	}
}
