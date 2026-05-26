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
import com.helpdesk.helpdesk.service.ClientCompanyRegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/client-companies")
public class ClientCompanyController {

	private final ClientCompanyRegistrationService clientCompanyRegistrationService;

	public ClientCompanyController(ClientCompanyRegistrationService clientCompanyRegistrationService) {
		this.clientCompanyRegistrationService = clientCompanyRegistrationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ClientCompanyRegistrationResponse create(@Valid @RequestBody CreateClientCompanyRequest request) {
		return clientCompanyRegistrationService.register(request);
	}

	@GetMapping("/lookup")
	public ClientCompanyLookupResponse lookup(
		@RequestParam String companyDocument,
		@RequestParam String createdByEmail
	) {
		return clientCompanyRegistrationService.lookup(companyDocument, createdByEmail);
	}

	@PostMapping("/link-existing")
	public ClientCompanyRegistrationResponse linkExisting(@Valid @RequestBody LinkExistingClientCompanyRequest request) {
		return clientCompanyRegistrationService.linkExisting(request);
	}
}
