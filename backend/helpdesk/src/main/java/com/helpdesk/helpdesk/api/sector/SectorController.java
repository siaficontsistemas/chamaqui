package com.helpdesk.helpdesk.api.sector;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.sector.CreateSectorRequest;
import com.helpdesk.helpdesk.dto.sector.SectorResponse;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.SectorService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sectors")
public class SectorController {

	private final SectorService sectorService;
	private final AppSessionService appSessionService;

	public SectorController(SectorService sectorService, AppSessionService appSessionService) {
		this.sectorService = sectorService;
		this.appSessionService = appSessionService;
	}

	@GetMapping
	public List<SectorResponse> list(HttpSession session) {
		return sectorService.listVisible(appSessionService.requireCurrentEmail(session));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SectorResponse create(@Valid @RequestBody CreateSectorRequest request, HttpSession session) {
		return sectorService.create(
			new CreateSectorRequest(
				request.name(),
				request.description(),
				appSessionService.requireCurrentEmail(session)
			)
		);
	}
}
