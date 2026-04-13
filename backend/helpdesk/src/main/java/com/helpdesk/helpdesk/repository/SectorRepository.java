package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.Sector;

public interface SectorRepository extends JpaRepository<Sector, UUID> {

	@EntityGraph(attributePaths = "createdBy")
	List<Sector> findByArchivedAtIsNullOrderByNameAsc();

	boolean existsBySlug(String slug);

	Optional<Sector> findBySlug(String slug);
}
