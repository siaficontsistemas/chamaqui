package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.TicketStatus;

public interface TicketStatusRepository extends JpaRepository<TicketStatus, UUID> {

	List<TicketStatus> findAllByOrderBySortOrderAsc();

	Optional<TicketStatus> findByCode(String code);
}
