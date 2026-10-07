package com.helpdesk.helpdesk.api.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.client.PreRegisterClientRequest;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientResponse;
import com.helpdesk.helpdesk.service.ClientPreRegistrationService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/client-pre-registrations")
public class ClientPreRegistrationController {
	private final ClientPreRegistrationService service;
	public ClientPreRegistrationController(ClientPreRegistrationService service) { this.service = service; }

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PreRegisterClientResponse create(@Valid @RequestBody PreRegisterClientRequest request, HttpSession session) {
		return service.create(request, session);
	}
}
