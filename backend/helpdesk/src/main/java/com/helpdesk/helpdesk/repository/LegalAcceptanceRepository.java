package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.LegalAcceptance;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, UUID> {

	List<LegalAcceptance> findByUserIdOrderByAcceptedAtDesc(UUID userId);
}
