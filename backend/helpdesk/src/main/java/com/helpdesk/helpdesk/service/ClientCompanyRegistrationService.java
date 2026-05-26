package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Locale;

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
import com.helpdesk.helpdesk.util.BrazilianDocumentValidator;

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
		String normalizedEmail = normalizeEmail(request.email());
		String normalizedCompanyDocument = normalizeCompanyDocument(request.companyDocument());
		String normalizedDocumentNumber = normalizeDocumentNumber(request.documentNumber());

		emailDomainValidationService.ensurePublicEmailDomainExists(normalizedEmail);
		cnpjLookupService.ensureCompanyExists(normalizedCompanyDocument);

		if (!BrazilianDocumentValidator.isValidCpf(normalizedDocumentNumber)) {
			throw new IllegalArgumentException("Informe um CPF válido para o administrador da empresa cliente.");
		}

		User existingUserByEmail = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizedEmail).orElse(null);
		if (existingUserByEmail != null && tenantAccessService.belongsToCurrentTenant(existingUserByEmail)) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse email.");
		}

		User existingCompany = findTenantScopedAdminCompanyByDocument(normalizedCompanyDocument);
		if (existingCompany != null) {
			throw new IllegalArgumentException("Já existe uma empresa cadastrada com esse CNPJ.");
		}

		Role adminRole = roleRepository.findByCode("ADMIN")
			.orElseThrow(() -> new NotFoundException("Perfil de administrador não encontrado."));

		User clientAdmin = new User();
		clientAdmin.setFullName(request.fullName().trim());
		clientAdmin.setEmail(normalizedEmail);
		clientAdmin.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));
		clientAdmin.setDocumentNumber(normalizedDocumentNumber);
		clientAdmin.setCompanyName(request.companyName().trim());
		clientAdmin.setCompanyDocument(normalizedCompanyDocument);
		clientAdmin.setCompanyType(CompanyType.REQUESTER);
		clientAdmin.setCompanyOwner(providerAdmin);
		clientAdmin.setPasswordHash(passwordEncoder.encode(request.password()));
		clientAdmin.setStatus(UserStatus.ACTIVE);
		clientAdmin.setEmailVerified(true);
		clientAdmin.setSimplified(false);
		clientAdmin.getRoles().clear();
		clientAdmin.getRoles().add(adminRole);

		User savedClientAdmin = userRepository.save(clientAdmin);
		ensureAcceptedPartnership(providerAdmin, savedClientAdmin);
		return toResponse(savedClientAdmin, providerAdmin);
	}

	@Transactional(readOnly = true)
	public ClientCompanyLookupResponse lookup(String companyDocument, String createdByEmail) {
		User providerAdmin = loadProviderAdmin(createdByEmail);
		String normalizedCompanyDocument = normalizeCompanyDocument(companyDocument);
		User existingCompany = findTenantScopedAdminCompanyByDocument(normalizedCompanyDocument);

		if (existingCompany == null) {
			return new ClientCompanyLookupResponse("AVAILABLE", null, null, null, null, null, null);
		}

		if (existingCompany.getId().equals(providerAdmin.getId()) || existingCompany.getCompanyType() != CompanyType.REQUESTER) {
			return new ClientCompanyLookupResponse(
				"UNAVAILABLE",
				"Esse CNPJ já está vinculado a um cadastro que não pode ser usado como empresa cliente.",
				existingCompany.getId(),
				existingCompany.getCompanyName(),
				existingCompany.getCompanyDocument(),
				existingCompany.getFullName(),
				existingCompany.getEmail()
			);
		}

		CompanyPartnership partnership = findExistingPartnership(providerAdmin, existingCompany);
		if (partnership != null) {
			String status = partnership.getStatus() == CompanyPartnershipStatus.ACCEPTED ? "ALREADY_CLIENT" : "PENDING_LINK";
			String message = partnership.getStatus() == CompanyPartnershipStatus.ACCEPTED
				? "A empresa desse CNPJ já está vinculada à sua operação."
				: "Já existe uma solicitação pendente para vincular essa empresa cliente.";
			return new ClientCompanyLookupResponse(
				status,
				message,
				existingCompany.getId(),
				existingCompany.getCompanyName(),
				existingCompany.getCompanyDocument(),
				existingCompany.getFullName(),
				existingCompany.getEmail()
			);
		}

		return new ClientCompanyLookupResponse(
			"CAN_LINK_EXISTING",
			"Esse CNPJ já está cadastrado e pode ser vinculado como empresa cliente.",
			existingCompany.getId(),
			existingCompany.getCompanyName(),
			existingCompany.getCompanyDocument(),
			existingCompany.getFullName(),
			existingCompany.getEmail()
		);
	}

	@Transactional
	public ClientCompanyRegistrationResponse linkExisting(LinkExistingClientCompanyRequest request) {
		User providerAdmin = loadProviderAdmin(request.createdByEmail());
		User existingCompany = userRepository.findAdminCompanyOwnerByIdAndCompanyType(
			request.companyOwnerId(),
			CompanyType.REQUESTER
		).orElseThrow(() -> new NotFoundException("Empresa cliente não encontrada."));

		if (!tenantAccessService.belongsToCurrentTenant(existingCompany)) {
			throw new NotFoundException("Empresa cliente não encontrada.");
		}
		if (existingCompany.getId().equals(providerAdmin.getId())) {
			throw new IllegalArgumentException("Você não pode vincular a própria empresa como cliente.");
		}

		CompanyPartnership partnership = findExistingPartnership(providerAdmin, existingCompany);
		if (partnership != null) {
			if (partnership.getStatus() == CompanyPartnershipStatus.ACCEPTED) {
				throw new IllegalArgumentException("Essa empresa já está vinculada como cliente.");
			}
			throw new IllegalArgumentException("Já existe uma solicitação pendente para essa empresa cliente.");
		}

		ensureAcceptedPartnership(providerAdmin, existingCompany);
		return toResponse(existingCompany, providerAdmin);
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

	private ClientCompanyRegistrationResponse toResponse(User clientAdmin, User providerAdmin) {
		String subdomain = tenantAccessService.findPrimaryCompanyForUser(providerAdmin)
			.map(Company::getSubdomain)
			.orElse(null);

		return new ClientCompanyRegistrationResponse(
			clientAdmin.getId(),
			clientAdmin.getCompanyName(),
			clientAdmin.getCompanyDocument(),
			clientAdmin.getFullName(),
			clientAdmin.getEmail(),
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

	private String normalizeDocumentNumber(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Informe o CPF do administrador.");
		}
		return value.trim().replaceAll("\\D", "");
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
		return value.trim();
	}
}
