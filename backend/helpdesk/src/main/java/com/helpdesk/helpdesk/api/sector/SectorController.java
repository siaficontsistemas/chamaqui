package com.helpdesk.helpdesk.api.sector;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.sector.CreateSectorRequest;
import com.helpdesk.helpdesk.dto.sector.SectorResponse;
import com.helpdesk.helpdesk.service.SectorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sectors")
public class SectorController {

	private final SectorService sectorService;

	public SectorController(SectorService sectorService) {
		this.sectorService = sectorService;
	}

	@GetMapping
	public List<SectorResponse> list() {
		return sectorService.listAll();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SectorResponse create(@Valid @RequestBody CreateSectorRequest request) {
		return sectorService.create(request);
	}
}
