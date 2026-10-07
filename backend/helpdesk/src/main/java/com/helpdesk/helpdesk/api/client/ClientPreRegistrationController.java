package com.helpdesk.helpdesk.api.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.client.PreRegisterClientRequest;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientResponse;
import com.helpdesk.helpdesk.dto.client.UpdatePreRegisterClientRequest;
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

	@PutMapping("/{clientId}")
	public PreRegisterClientResponse update(
		@PathVariable java.util.UUID clientId,
		@Valid @RequestBody UpdatePreRegisterClientRequest request,
		HttpSession session
	) {
		return service.update(clientId, request, session);
	}

	@DeleteMapping("/{clientId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable java.util.UUID clientId, HttpSession session) {
		service.delete(clientId, session);
	}
}
