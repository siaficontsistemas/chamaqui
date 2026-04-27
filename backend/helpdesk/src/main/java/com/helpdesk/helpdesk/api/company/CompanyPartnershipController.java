package com.helpdesk.helpdesk.api.company;

import java.util.List;
import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.company.CompanyPartnershipResponse;
import com.helpdesk.helpdesk.dto.company.CompanySearchResultResponse;
import com.helpdesk.helpdesk.dto.company.CreateCompanyPartnershipRequest;
import com.helpdesk.helpdesk.dto.company.RespondCompanyPartnershipRequest;
import com.helpdesk.helpdesk.dto.sector.SectorResponse;
import com.helpdesk.helpdesk.service.CompanyPartnershipService;

import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/v1/company-partnerships")
public class CompanyPartnershipController {

	private final CompanyPartnershipService companyPartnershipService;

	public CompanyPartnershipController(CompanyPartnershipService companyPartnershipService) {
		this.companyPartnershipService = companyPartnershipService;
	}

	@GetMapping("/search")
	public List<CompanySearchResultResponse> searchCompanies(
		@RequestParam("email") String email,
		@RequestParam("query") String query
	) {
		return companyPartnershipService.searchCompanies(email, query);
	}

	@GetMapping("/mine")
	public List<CompanyPartnershipResponse> listMine(@RequestParam("email") String email) {
		return companyPartnershipService.listMine(email);
	}

	@GetMapping("/ticket-targets")
	public List<SectorResponse> listTicketTargets(@RequestParam("email") String email) {
		return companyPartnershipService.listTicketTargets(email);
	}

	@PostMapping
	public CompanyPartnershipResponse create(@Valid @RequestBody CreateCompanyPartnershipRequest request) {
		return companyPartnershipService.create(request);
	}

	@PostMapping("/{partnershipId}/accept")
	public CompanyPartnershipResponse accept(
		@PathVariable UUID partnershipId,
		@Valid @RequestBody RespondCompanyPartnershipRequest request
	) {
		return companyPartnershipService.accept(partnershipId, request);
	}

	@PostMapping("/{partnershipId}/decline")
	public CompanyPartnershipResponse decline(
		@PathVariable UUID partnershipId,
		@Valid @RequestBody RespondCompanyPartnershipRequest request
	) {
		return companyPartnershipService.decline(partnershipId, request);
	}

	@DeleteMapping("/{partnershipId}")
	public void unlink(
		@PathVariable UUID partnershipId,
		@RequestParam("email") String email
	) {
		companyPartnershipService.unlink(partnershipId, email);
	}
}
