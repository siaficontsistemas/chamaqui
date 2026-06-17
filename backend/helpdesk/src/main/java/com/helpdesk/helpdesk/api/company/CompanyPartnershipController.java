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
import com.helpdesk.helpdesk.dto.ticket.TicketTargetSectorResponse;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.CompanyPartnershipService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/v1/company-partnerships")
public class CompanyPartnershipController {

	private final CompanyPartnershipService companyPartnershipService;
	private final AppSessionService appSessionService;

	public CompanyPartnershipController(
		CompanyPartnershipService companyPartnershipService,
		AppSessionService appSessionService
	) {
		this.companyPartnershipService = companyPartnershipService;
		this.appSessionService = appSessionService;
	}

	@GetMapping("/search")
	public List<CompanySearchResultResponse> searchCompanies(
		@RequestParam("query") String query,
		HttpSession session
	) {
		return companyPartnershipService.searchCompanies(appSessionService.requireCurrentEmail(session), query);
	}

	@GetMapping("/mine")
	public List<CompanyPartnershipResponse> listMine(HttpSession session) {
		return companyPartnershipService.listMine(appSessionService.requireCurrentEmail(session));
	}

	@GetMapping("/ticket-targets")
	public List<TicketTargetSectorResponse> listTicketTargets(HttpSession session) {
		return companyPartnershipService.listTicketTargets(appSessionService.requireCurrentEmail(session));
	}

	@PostMapping
	public CompanyPartnershipResponse create(
		@Valid @RequestBody CreateCompanyPartnershipRequest request,
		HttpSession session
	) {
		return companyPartnershipService.create(
			new CreateCompanyPartnershipRequest(appSessionService.requireCurrentEmail(session), request.targetCompanyId())
		);
	}

	@PostMapping("/{partnershipId}/accept")
	public CompanyPartnershipResponse accept(
		@PathVariable UUID partnershipId,
		@Valid @RequestBody RespondCompanyPartnershipRequest request,
		HttpSession session
	) {
		return companyPartnershipService.accept(
			partnershipId,
			new RespondCompanyPartnershipRequest(appSessionService.requireCurrentEmail(session))
		);
	}

	@PostMapping("/{partnershipId}/decline")
	public CompanyPartnershipResponse decline(
		@PathVariable UUID partnershipId,
		@Valid @RequestBody RespondCompanyPartnershipRequest request,
		HttpSession session
	) {
		return companyPartnershipService.decline(
			partnershipId,
			new RespondCompanyPartnershipRequest(appSessionService.requireCurrentEmail(session))
		);
	}

	@DeleteMapping("/{partnershipId}")
	public void unlink(
		@PathVariable UUID partnershipId,
		HttpSession session
	) {
		companyPartnershipService.unlink(partnershipId, appSessionService.requireCurrentEmail(session));
	}
}
