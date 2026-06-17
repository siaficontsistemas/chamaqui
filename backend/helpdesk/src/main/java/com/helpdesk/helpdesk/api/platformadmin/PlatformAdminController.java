package com.helpdesk.helpdesk.api.platformadmin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.common.OperationMessageResponse;
import com.helpdesk.helpdesk.dto.platformadmin.CreatePlatformCompanyRequest;
import com.helpdesk.helpdesk.dto.platformadmin.PlatformAdminAuthResponse;
import com.helpdesk.helpdesk.dto.platformadmin.PlatformAdminLoginRequest;
import com.helpdesk.helpdesk.dto.platformadmin.PlatformCompanySummaryResponse;
import com.helpdesk.helpdesk.service.PlatformAdminCompanyService;
import com.helpdesk.helpdesk.service.PlatformAdminSessionService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/platform-admin")
public class PlatformAdminController {

	private final PlatformAdminSessionService platformAdminSessionService;
	private final PlatformAdminCompanyService platformAdminCompanyService;

	public PlatformAdminController(
		PlatformAdminSessionService platformAdminSessionService,
		PlatformAdminCompanyService platformAdminCompanyService
	) {
		this.platformAdminSessionService = platformAdminSessionService;
		this.platformAdminCompanyService = platformAdminCompanyService;
	}

	@PostMapping("/auth/login")
	public PlatformAdminAuthResponse login(
		@Valid @RequestBody PlatformAdminLoginRequest request,
		HttpSession session
	) {
		return platformAdminSessionService.login(request, session);
	}

	@GetMapping("/auth/me")
	public PlatformAdminAuthResponse me(HttpSession session) {
		return platformAdminSessionService.me(session);
	}

	@PostMapping("/auth/logout")
	public OperationMessageResponse logout(HttpSession session) {
		platformAdminSessionService.logout(session);
		return new OperationMessageResponse("Sessão do administrador da plataforma encerrada com sucesso.");
	}

	@GetMapping("/companies")
	public List<PlatformCompanySummaryResponse> listCompanies(HttpSession session) {
		platformAdminSessionService.requireUser(session);
		return platformAdminCompanyService.listResponderCompanies();
	}

	@PostMapping("/companies")
	@ResponseStatus(HttpStatus.CREATED)
	public PlatformCompanySummaryResponse createCompany(
		@Valid @RequestBody CreatePlatformCompanyRequest request,
		HttpSession session
	) {
		return platformAdminCompanyService.createResponderCompany(request, platformAdminSessionService.requireUser(session));
	}

	@PatchMapping("/companies/{companyId}/deactivate")
	public PlatformCompanySummaryResponse deactivateCompany(
		@PathVariable UUID companyId,
		HttpSession session
	) {
		return platformAdminCompanyService.deactivateResponderCompany(
			companyId,
			platformAdminSessionService.requireUser(session)
		);
	}

	@PatchMapping("/companies/{companyId}/activate")
	public PlatformCompanySummaryResponse activateCompany(
		@PathVariable UUID companyId,
		HttpSession session
	) {
		return platformAdminCompanyService.activateResponderCompany(
			companyId,
			platformAdminSessionService.requireUser(session)
		);
	}
}
