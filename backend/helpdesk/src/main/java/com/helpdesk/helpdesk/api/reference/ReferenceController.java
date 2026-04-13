package com.helpdesk.helpdesk.api.reference;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.reference.ReferenceItemResponse;
import com.helpdesk.helpdesk.service.ReferenceService;

@RestController
@RequestMapping("/api/v1/reference")
public class ReferenceController {

	private final ReferenceService referenceService;

	public ReferenceController(ReferenceService referenceService) {
		this.referenceService = referenceService;
	}

	@GetMapping("/roles")
	public List<ReferenceItemResponse> getRoles() {
		return referenceService.getRoles();
	}

	@GetMapping("/ticket-statuses")
	public List<ReferenceItemResponse> getTicketStatuses() {
		return referenceService.getTicketStatuses();
	}

	@GetMapping("/ticket-priorities")
	public List<ReferenceItemResponse> getTicketPriorities() {
		return referenceService.getTicketPriorities();
	}
}
