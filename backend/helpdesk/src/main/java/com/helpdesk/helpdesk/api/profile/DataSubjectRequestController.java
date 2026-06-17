package com.helpdesk.helpdesk.api.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.profile.CreateDataSubjectRequestRequest;
import com.helpdesk.helpdesk.dto.profile.DataSubjectRequestResponse;
import com.helpdesk.helpdesk.dto.profile.ManageDataSubjectRequestRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.DataSubjectRequestService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile/data-rights-requests")
public class DataSubjectRequestController {

	private final DataSubjectRequestService dataSubjectRequestService;
	private final AppSessionService appSessionService;

	public DataSubjectRequestController(
		DataSubjectRequestService dataSubjectRequestService,
		AppSessionService appSessionService
	) {
		this.dataSubjectRequestService = dataSubjectRequestService;
		this.appSessionService = appSessionService;
	}

	@GetMapping
	public List<DataSubjectRequestResponse> listMine(HttpSession session) {
		return dataSubjectRequestService.listMine(appSessionService.requireUser(session));
	}

	@PostMapping
	public DataSubjectRequestResponse create(
		@Valid @RequestBody CreateDataSubjectRequestRequest request,
		HttpSession session
	) {
		return dataSubjectRequestService.create(appSessionService.requireUser(session), request);
	}

	@GetMapping("/manage")
	public List<DataSubjectRequestResponse> listForAdmin(HttpSession session) {
		return dataSubjectRequestService.listForAdmin(appSessionService.requireUser(session));
	}

	@PatchMapping("/{requestId}")
	public DataSubjectRequestResponse manage(
		@PathVariable UUID requestId,
		@Valid @RequestBody ManageDataSubjectRequestRequest request,
		HttpSession session
	) {
		return dataSubjectRequestService.manage(appSessionService.requireUser(session), requestId, request);
	}
}
