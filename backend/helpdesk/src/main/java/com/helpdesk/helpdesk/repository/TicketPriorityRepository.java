package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.TicketPriority;

public interface TicketPriorityRepository extends JpaRepository<TicketPriority, UUID> {

	List<TicketPriority> findAllByOrderBySortOrderAsc();

	Optional<TicketPriority> findByCode(String code);
}
