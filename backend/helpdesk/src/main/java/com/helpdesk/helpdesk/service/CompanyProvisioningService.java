package com.helpdesk.helpdesk.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.domain.Company;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.repository.CompanyRepository;

@Service
public class CompanyProvisioningService {

	private static final int MAX_SUBDOMAIN_LENGTH = 80;
	private static final int MAX_SCHEMA_NAME_LENGTH = 80;

	private final CompanyRepository companyRepository;
	private final JdbcTemplate jdbcTemplate;
	private final TenantSchemaProvisioningService tenantSchemaProvisioningService;
	private final Set<String> reservedSubdomains;

	public CompanyProvisioningService(
		CompanyRepository companyRepository,
		JdbcTemplate jdbcTemplate,
		TenantSchemaProvisioningService tenantSchemaProvisioningService,
		@Value("${app.tenancy.reserved-subdomains:admin,api,www}") String reservedSubdomains
	) {
		this.companyRepository = companyRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.tenantSchemaProvisioningService = tenantSchemaProvisioningService;
		this.reservedSubdomains = Arrays.stream(reservedSubdomains.split(","))
			.map(this::sanitizeSlug)
			.filter(value -> !value.isBlank())
			.collect(Collectors.toUnmodifiableSet());
	}

	@Transactional
	public Company syncAdminCompany(User admin) {
		return syncAdminCompany(admin, null);
	}

	@Transactional
	public Company syncAdminCompany(User admin, String preferredSubdomain) {
		Company company = companyRepository.findByOwnerUserId(admin.getId()).orElseGet(Company::new);
		boolean isNewCompany = company.getId() == null;

		if (isNewCompany) {
			company.setOwnerUser(admin);
			company.setSubdomain(generateUniqueSubdomain(preferredSubdomain, admin.getCompanyName(), null));
			company.setSchemaName(generateUniqueSchemaName(preferredSubdomain, admin.getCompanyName(), null));
		}

		company.setCompanyName(admin.getCompanyName().trim());
		company.setCompanyDocument(admin.getCompanyDocument().replaceAll("\\D", ""));
		company.setCompanyType(admin.getCompanyType());
		company.setActive(true);

		Company savedCompany = companyRepository.save(company);
		ensureSchemaExists(savedCompany.getSchemaName());
		tenantSchemaProvisioningService.ensureTenantSchemaStructure(savedCompany.getSchemaName());
		return savedCompany;
	}

	@Transactional
	public void deleteCompanyRegistration(UUID ownerUserId) {
		companyRepository.deleteByOwnerUserId(ownerUserId);
	}

	public void ensureSubdomainAllowed(String subdomain, UUID currentCompanyId) {
		String sanitizedSubdomain = sanitizeSlug(subdomain);
		if (sanitizedSubdomain.isBlank()) {
			throw new IllegalArgumentException("Informe um subdomínio válido para a empresa.");
		}
		if (reservedSubdomains.contains(sanitizedSubdomain)) {
			throw new IllegalArgumentException("Esse subdomínio é reservado para uso interno da plataforma.");
		}
		if (isSubdomainInUse(sanitizedSubdomain, currentCompanyId)) {
			throw new IllegalArgumentException("Esse subdomínio já está em uso por outra empresa.");
		}
	}

	private String generateUniqueSubdomain(String preferredSubdomain, String companyName, UUID currentCompanyId) {
		String baseValue = sanitizeSlug(preferredSubdomain);
		if (baseValue.isBlank()) {
			baseValue = sanitizeSlug(companyName);
		}
		String candidate = trimToMaxLength(baseValue, MAX_SUBDOMAIN_LENGTH);
		int suffix = 2;

		if (reservedSubdomains.contains(candidate)) {
			candidate = trimToMaxLength(baseValue + "empresa", MAX_SUBDOMAIN_LENGTH);
		}

		while (isSubdomainInUse(candidate, currentCompanyId)) {
			String suffixValue = String.valueOf(suffix);
			candidate = trimToMaxLength(baseValue, MAX_SUBDOMAIN_LENGTH - suffixValue.length()) + suffixValue;
			if (reservedSubdomains.contains(candidate)) {
				candidate = trimToMaxLength(baseValue + "empresa", MAX_SUBDOMAIN_LENGTH - suffixValue.length()) + suffixValue;
			}
			suffix += 1;
		}

		return candidate;
	}

	private String generateUniqueSchemaName(String preferredSubdomain, String companyName, UUID currentCompanyId) {
		String schemaBase = sanitizeSlug(preferredSubdomain);
		if (schemaBase.isBlank()) {
			schemaBase = sanitizeSlug(companyName);
		}
		String baseValue = "tenant_" + schemaBase.replace('-', '_');
		String candidate = trimToMaxLength(baseValue, MAX_SCHEMA_NAME_LENGTH);
		int suffix = 2;

		while (isSchemaNameInUse(candidate, currentCompanyId)) {
			String suffixValue = "_" + suffix;
			candidate = trimToMaxLength(baseValue, MAX_SCHEMA_NAME_LENGTH - suffixValue.length()) + suffixValue;
			suffix += 1;
		}

		return candidate;
	}

	private boolean isSubdomainInUse(String subdomain, UUID currentCompanyId) {
		if (currentCompanyId == null) {
			return companyRepository.existsBySubdomainIgnoreCase(subdomain);
		}
		return companyRepository.existsBySubdomainIgnoreCaseAndIdNot(subdomain, currentCompanyId);
	}

	private boolean isSchemaNameInUse(String schemaName, UUID currentCompanyId) {
		if (currentCompanyId == null) {
			return companyRepository.existsBySchemaNameIgnoreCase(schemaName);
		}
		return companyRepository.existsBySchemaNameIgnoreCaseAndIdNot(schemaName, currentCompanyId);
	}

	private void ensureSchemaExists(String schemaName) {
		jdbcTemplate.execute("create schema if not exists " + quoteIdentifier(schemaName));
	}

	private String quoteIdentifier(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String sanitizeSlug(String value) {
		String normalizedValue = Normalizer.normalize(String.valueOf(value), Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "");

		if (normalizedValue.isBlank()) {
			return "empresa";
		}

		return normalizedValue;
	}

	private String trimToMaxLength(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength).replaceAll("[_]+$", "");
	}
}
