package com.helpdesk.helpdesk.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Sector;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.sector.CreateSectorRequest;
import com.helpdesk.helpdesk.dto.sector.SectorResponse;
import com.helpdesk.helpdesk.repository.SectorRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class SectorService {

	private final SectorRepository sectorRepository;
	private final UserRepository userRepository;
	private final SlugService slugService;

	public SectorService(
		SectorRepository sectorRepository,
		UserRepository userRepository,
		SlugService slugService
	) {
		this.sectorRepository = sectorRepository;
		this.userRepository = userRepository;
		this.slugService = slugService;
	}

	@Transactional(readOnly = true)
	public List<SectorResponse> listAll() {
		return sectorRepository.findByArchivedAtIsNullOrderByNameAsc().stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public SectorResponse create(CreateSectorRequest request) {
		User createdBy = userRepository.findByEmailIgnoreCase(request.createdByEmail().trim())
			.orElseThrow(() -> new NotFoundException("Usuário responsável pelo setor não encontrado."));

		String baseSlug = slugService.slugify(request.name());
		String slug = baseSlug;
		int suffix = 2;
		while (sectorRepository.existsBySlug(slug)) {
			slug = baseSlug + "-" + suffix;
			suffix++;
		}

		Sector sector = new Sector();
		sector.setName(request.name().trim());
		sector.setSlug(slug);
		sector.setDescription(request.description() == null ? null : request.description().trim());
		sector.setCreatedBy(createdBy);
		sector.setActive(true);

		return toResponse(sectorRepository.save(sector));
	}

	@Transactional(readOnly = true)
	public Sector getSector(java.util.UUID sectorId) {
		return sectorRepository.findById(sectorId)
			.orElseThrow(() -> new NotFoundException("Setor não encontrado."));
	}

	private SectorResponse toResponse(Sector sector) {
		return new SectorResponse(
			sector.getId(),
			sector.getName(),
			sector.getSlug(),
			sector.getDescription(),
			sector.isActive(),
			sector.getCreatedBy().getEmail(),
			sector.getMembers().size()
		);
	}
}
