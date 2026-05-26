package com.helpdesk.helpdesk.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.helpdesk.helpdesk.domain.Company;
import com.helpdesk.helpdesk.repository.CompanyRepository;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;

@Service
public class TenantResolutionService {

	private final CompanyRepository companyRepository;
	private final String baseDomain;
	private final Set<String> mainHosts;
	private final Set<String> reservedSubdomains;

	public TenantResolutionService(
		CompanyRepository companyRepository,
		@Value("${app.tenancy.base-domain:chamaqui.app.br}") String baseDomain,
		@Value("${app.tenancy.main-hosts:localhost,127.0.0.1,chamaqui.app.br,www.chamaqui.app.br}") String mainHosts,
		@Value("${app.tenancy.reserved-subdomains:admin,api,www}") String reservedSubdomains
	) {
		this.companyRepository = companyRepository;
		this.baseDomain = normalizeHost(baseDomain);
		this.mainHosts = Arrays.stream(mainHosts.split(","))
			.map(this::normalizeHost)
			.filter(value -> !value.isBlank())
			.collect(Collectors.toUnmodifiableSet());
		this.reservedSubdomains = Arrays.stream(reservedSubdomains.split(","))
			.map(this::normalizeHost)
			.filter(value -> !value.isBlank())
			.collect(Collectors.toUnmodifiableSet());
	}

	public Optional<ResolvedTenant> resolve(String host) {
		String normalizedHost = normalizeHost(host);
		if (normalizedHost.isBlank() || mainHosts.contains(normalizedHost)) {
			return Optional.empty();
		}

		String subdomain = extractSubdomain(normalizedHost);
		if (subdomain == null || reservedSubdomains.contains(subdomain)) {
			return Optional.empty();
		}

		return companyRepository.findBySubdomainIgnoreCaseAndActiveTrue(subdomain)
			.map(this::toResolvedTenant);
	}

	private ResolvedTenant toResolvedTenant(Company company) {
		return new ResolvedTenant(
			company.getId(),
			company.getOwnerUser() == null ? null : company.getOwnerUser().getId(),
			company.getCompanyName(),
			company.getCompanyType() == null ? null : company.getCompanyType().name(),
			company.getSubdomain(),
			company.getSchemaName(),
			company.getLogoUrl(),
			company.getLoginLogoUrl()
		);
	}

	private String extractSubdomain(String host) {
		if (host.isBlank() || host.equals(baseDomain)) {
			return null;
		}

		String suffix = "." + baseDomain;
		if (!host.endsWith(suffix)) {
			return null;
		}

		String candidate = host.substring(0, host.length() - suffix.length());
		if (candidate.isBlank() || candidate.contains(".")) {
			return null;
		}

		return candidate;
	}

	private String normalizeHost(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}

		String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
		int commaIndex = normalizedValue.indexOf(',');
		if (commaIndex >= 0) {
			normalizedValue = normalizedValue.substring(0, commaIndex).trim();
		}
		int colonIndex = normalizedValue.indexOf(':');
		if (colonIndex >= 0) {
			normalizedValue = normalizedValue.substring(0, colonIndex);
		}
		if (normalizedValue.endsWith(".")) {
			normalizedValue = normalizedValue.substring(0, normalizedValue.length() - 1);
		}
		return normalizedValue;
	}
}
