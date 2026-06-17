package com.helpdesk.helpdesk.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Company;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.PlatformAdminUser;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.platformadmin.CreatePlatformCompanyRequest;
import com.helpdesk.helpdesk.dto.platformadmin.PlatformCompanySummaryResponse;
import com.helpdesk.helpdesk.repository.CompanyRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.util.BrazilianDocumentValidator;

@Service
public class PlatformAdminCompanyService {

	private final CompanyRepository companyRepository;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final CompanyProvisioningService companyProvisioningService;
	private final CnpjLookupService cnpjLookupService;
	private final EmailDomainValidationService emailDomainValidationService;
	private final AuditTrailService auditTrailService;

	public PlatformAdminCompanyService(
		CompanyRepository companyRepository,
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		CompanyProvisioningService companyProvisioningService,
		CnpjLookupService cnpjLookupService,
		EmailDomainValidationService emailDomainValidationService,
		AuditTrailService auditTrailService
	) {
		this.companyRepository = companyRepository;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.companyProvisioningService = companyProvisioningService;
		this.cnpjLookupService = cnpjLookupService;
		this.emailDomainValidationService = emailDomainValidationService;
		this.auditTrailService = auditTrailService;
	}

	@Transactional(readOnly = true)
	public List<PlatformCompanySummaryResponse> listResponderCompanies() {
		return companyRepository.findAllByOrderByCompanyNameAsc().stream()
			.filter(company -> company.getCompanyType() == CompanyType.RESPONDER)
			.map(this::toSummary)
			.toList();
	}

	@Transactional
	public PlatformCompanySummaryResponse createResponderCompany(CreatePlatformCompanyRequest request, PlatformAdminUser actor) {
		String normalizedCompanyName = requireText(request.companyName(), "Informe o nome da empresa.");
		String normalizedCompanyDocument = normalizeDigits(request.companyDocument());
		String normalizedSubdomain = requireText(request.subdomain(), "Informe o subdomínio desejado.");
		String normalizedAdminName = requireText(request.adminFullName(), "Informe o nome do administrador.");
		String normalizedAdminEmail = normalizeEmail(request.adminEmail());
		String normalizedAdminPhone = normalizeOptionalDigits(request.adminPhoneNumber());
		String normalizedAdminDocument = normalizeDigits(request.adminDocumentNumber());
		String normalizedAdminPassword = request.adminPassword() == null ? "" : request.adminPassword().trim();

		if (normalizedCompanyDocument.length() != 14) {
			throw new IllegalArgumentException("Informe um CNPJ válido para a empresa.");
		}
		if (normalizedAdminDocument.length() != 11 || !BrazilianDocumentValidator.isValidCpf(normalizedAdminDocument)) {
			throw new IllegalArgumentException("Informe um CPF válido para o administrador.");
		}
		if (normalizedAdminPassword.length() < 6) {
			throw new IllegalArgumentException("A senha inicial do administrador deve ter pelo menos 6 caracteres.");
		}

		emailDomainValidationService.ensurePublicEmailDomainExists(normalizedAdminEmail);
		cnpjLookupService.ensureCompanyExists(normalizedCompanyDocument);
		companyProvisioningService.ensureSubdomainAllowed(normalizedSubdomain, null);

		if (companyRepository.existsByCompanyDocument(normalizedCompanyDocument)) {
			throw new IllegalArgumentException("Já existe uma empresa respondedora cadastrada com esse CNPJ.");
		}

		Role adminRole = roleRepository.findByCode("ADMIN")
			.orElseThrow(() -> new NotFoundException("Perfil ADMIN não encontrado."));

		User admin = new User();
		admin.setFullName(normalizedAdminName);
		admin.setEmail(normalizedAdminEmail);
		admin.setPhoneNumber(normalizedAdminPhone);
		admin.setDocumentNumber(normalizedAdminDocument);
		admin.setCompanyName(normalizedCompanyName);
		admin.setCompanyDocument(normalizedCompanyDocument);
		admin.setCompanyType(CompanyType.RESPONDER);
		admin.setCompanyOwner(null);
		admin.setPasswordHash(passwordEncoder.encode(normalizedAdminPassword));
		admin.setStatus(UserStatus.ACTIVE);
		admin.setEmailVerified(true);
		admin.setSimplified(false);
		admin.getRoles().clear();
		admin.getRoles().add(adminRole);

		User savedAdmin = userRepository.save(admin);
		Company savedCompany = companyProvisioningService.syncAdminCompany(savedAdmin, normalizedSubdomain);
		auditTrailService.recordPlatformAdminAction("PLATFORM_COMPANY_CREATED", actor, "company", savedCompany.getId());
		return toSummary(savedCompany);
	}

	@Transactional
	public PlatformCompanySummaryResponse deactivateResponderCompany(UUID companyId, PlatformAdminUser actor) {
		Company company = loadResponderCompany(companyId);
		company.setActive(false);
		userRepository.findByCompanyOwnerIdOrIdOrderByCreatedAtAsc(company.getOwnerUser().getId(), company.getOwnerUser().getId())
			.forEach(user -> user.setStatus(UserStatus.INACTIVE));
		auditTrailService.recordPlatformAdminAction("PLATFORM_COMPANY_DEACTIVATED", actor, "company", company.getId());
		return toSummary(company);
	}

	@Transactional
	public PlatformCompanySummaryResponse activateResponderCompany(UUID companyId, PlatformAdminUser actor) {
		Company company = loadResponderCompany(companyId);
		company.setActive(true);
		userRepository.findByCompanyOwnerIdOrIdOrderByCreatedAtAsc(company.getOwnerUser().getId(), company.getOwnerUser().getId())
			.forEach(user -> {
				if (user.getStatus() == UserStatus.INACTIVE) {
					user.setStatus(UserStatus.ACTIVE);
				}
			});
		auditTrailService.recordPlatformAdminAction("PLATFORM_COMPANY_ACTIVATED", actor, "company", company.getId());
		return toSummary(company);
	}

	private Company loadResponderCompany(UUID companyId) {
		Company company = companyRepository.findById(companyId)
			.orElseThrow(() -> new NotFoundException("Empresa não encontrada."));
		if (company.getCompanyType() != CompanyType.RESPONDER) {
			throw new IllegalArgumentException("A área administrativa da plataforma gerencia apenas empresas respondedoras.");
		}
		return company;
	}

	private PlatformCompanySummaryResponse toSummary(Company company) {
		User owner = company.getOwnerUser();
		long activeUsersCount = userRepository.findActiveByCompanyOwnerId(owner.getId()).size();

		return new PlatformCompanySummaryResponse(
			company.getId(),
			company.getCompanyName(),
			company.getCompanyDocument(),
			company.getSubdomain(),
			company.getSchemaName(),
			company.isActive(),
			owner.getFullName(),
			owner.getEmail(),
			owner.getStatus().name(),
			activeUsersCount,
			company.getCreatedAt()
		);
	}

	private String normalizeEmail(String email) {
		String normalizedEmail = requireText(email, "Informe o email do administrador.");
		return normalizedEmail.toLowerCase(Locale.ROOT);
	}

	private String normalizeDigits(String value) {
		return requireText(value, "Informe o valor obrigatório.").replaceAll("\\D", "");
	}

	private String normalizeOptionalDigits(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.replaceAll("\\D", "");
	}

	private String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
