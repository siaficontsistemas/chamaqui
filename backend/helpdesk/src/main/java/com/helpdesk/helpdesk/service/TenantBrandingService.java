package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.helpdesk.helpdesk.dto.company.PublicCompanyBrandingResponse;
import com.helpdesk.helpdesk.dto.company.PublicTenantSummaryResponse;
import com.helpdesk.helpdesk.repository.CompanyRepository;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;

@Service
public class TenantBrandingService {

	private final CompanyRepository companyRepository;
	private final TenantResolutionService tenantResolutionService;
	private final FrontendPublicUrlService frontendPublicUrlService;

	public TenantBrandingService(
		CompanyRepository companyRepository,
		TenantResolutionService tenantResolutionService,
		FrontendPublicUrlService frontendPublicUrlService
	) {
		this.companyRepository = companyRepository;
		this.tenantResolutionService = tenantResolutionService;
		this.frontendPublicUrlService = frontendPublicUrlService;
	}

	public PublicCompanyBrandingResponse resolvePublicBranding(String host) {
		return tenantResolutionService.resolve(host)
			.map(this::toResponse)
			.orElseGet(this::emptyResponse);
	}

	private PublicCompanyBrandingResponse toResponse(ResolvedTenant tenant) {
		return new PublicCompanyBrandingResponse(
			true,
			tenant.ownerUserId(),
			tenant.companyName(),
			tenant.companyType(),
			tenant.subdomain(),
			tenant.logoUrl(),
			tenant.loginLogoUrl()
		);
	}

	private PublicCompanyBrandingResponse emptyResponse() {
		return new PublicCompanyBrandingResponse(false, null, null, null, null, null, null);
	}

	public List<PublicTenantSummaryResponse> listPublicTenants() {
		return companyRepository.findAllByActiveTrueOrderByCompanyNameAsc()
			.stream()
			.map(company -> new PublicTenantSummaryResponse(
				company.getCompanyName(),
				company.getSubdomain(),
				company.getSchemaName(),
				frontendPublicUrlService.buildAccessUrl(company.getSubdomain())
			))
			.toList();
	}
}
