package com.helpdesk.helpdesk.api.publicconfig;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.domain.LegalDocumentType;
import com.helpdesk.helpdesk.dto.company.PublicCompanyBrandingResponse;
import com.helpdesk.helpdesk.dto.company.PublicTenantSummaryResponse;
import com.helpdesk.helpdesk.dto.legal.PublicLegalDocumentResponse;
import com.helpdesk.helpdesk.service.CompanyLogoStorageService;
import com.helpdesk.helpdesk.service.LegalDocumentService;
import com.helpdesk.helpdesk.service.TenantBrandingService;
import com.helpdesk.helpdesk.util.RequestHostResolver;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/public")
public class PublicTenantController {

	private final TenantBrandingService tenantBrandingService;
	private final CompanyLogoStorageService companyLogoStorageService;
	private final LegalDocumentService legalDocumentService;

	public PublicTenantController(
		TenantBrandingService tenantBrandingService,
		CompanyLogoStorageService companyLogoStorageService,
		LegalDocumentService legalDocumentService
	) {
		this.tenantBrandingService = tenantBrandingService;
		this.companyLogoStorageService = companyLogoStorageService;
		this.legalDocumentService = legalDocumentService;
	}

	@GetMapping("/tenant-branding")
	public PublicCompanyBrandingResponse getTenantBranding(
		@RequestParam(required = false) String host,
		@RequestHeader(value = "X-Tenant-Host", required = false) String tenantHost,
		@RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost,
		HttpServletRequest request
	) {
		String resolvedHost = RequestHostResolver.firstNonBlank(
			host,
			tenantHost,
			RequestHostResolver.resolveTenantHost(request),
			forwardedHost
		);
		return tenantBrandingService.resolvePublicBranding(resolvedHost);
	}

	@GetMapping("/tenants")
	public List<PublicTenantSummaryResponse> listTenants() {
		return tenantBrandingService.listPublicTenants();
	}

	@GetMapping("/legal-documents")
	public List<PublicLegalDocumentResponse> listLegalDocuments() {
		return legalDocumentService.listPublicDocuments();
	}

	@GetMapping("/legal-documents/{documentType}")
	public PublicLegalDocumentResponse getLegalDocument(@PathVariable String documentType) {
		try {
			return legalDocumentService.getPublicDocument(LegalDocumentType.valueOf(documentType.trim().toUpperCase()));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Documento legal não encontrado.");
		}
	}

	@GetMapping("/company-assets/{storageKey}")
	public ResponseEntity<Resource> getCompanyAsset(@PathVariable String storageKey) {
		CompanyLogoStorageService.StoredCompanyLogoContent storedLogo = companyLogoStorageService.load(storageKey);
		Resource resource = storedLogo.resource();
		String contentType = storedLogo.contentType();

		return ResponseEntity.ok()
			.header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
			.contentType(
				contentType == null || contentType.isBlank()
					? MediaType.APPLICATION_OCTET_STREAM
					: MediaType.parseMediaType(contentType)
			)
			.body(resource);
	}
}
