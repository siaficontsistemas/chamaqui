package com.helpdesk.helpdesk.api.company;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.company.ClientCompanyRegistrationResponse;
import com.helpdesk.helpdesk.dto.company.ClientCompanyLookupResponse;
import com.helpdesk.helpdesk.dto.company.CreateClientCompanyRequest;
import com.helpdesk.helpdesk.dto.company.LinkExistingClientCompanyRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.ClientCompanyRegistrationService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/client-companies")
public class ClientCompanyController {

	private final ClientCompanyRegistrationService clientCompanyRegistrationService;
	private final AppSessionService appSessionService;

	public ClientCompanyController(
		ClientCompanyRegistrationService clientCompanyRegistrationService,
		AppSessionService appSessionService
	) {
		this.clientCompanyRegistrationService = clientCompanyRegistrationService;
		this.appSessionService = appSessionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ClientCompanyRegistrationResponse create(
		@Valid @RequestBody CreateClientCompanyRequest request,
		HttpSession session
	) {
		return clientCompanyRegistrationService.register(
			new CreateClientCompanyRequest(
				request.companyName(),
				request.companyDocument(),
				request.companyEmail(),
				request.companyPhoneNumber(),
				appSessionService.requireCurrentEmail(session)
			)
		);
	}

	@GetMapping("/lookup")
	public ClientCompanyLookupResponse lookup(
		@RequestParam String companyDocument,
		HttpSession session
	) {
		return clientCompanyRegistrationService.lookup(companyDocument, appSessionService.requireCurrentEmail(session));
	}

	@PostMapping("/link-existing")
	public ClientCompanyRegistrationResponse linkExisting(
		@Valid @RequestBody LinkExistingClientCompanyRequest request,
		HttpSession session
	) {
		return clientCompanyRegistrationService.linkExisting(
			new LinkExistingClientCompanyRequest(request.companyOwnerId(), appSessionService.requireCurrentEmail(session))
		);
	}
}
