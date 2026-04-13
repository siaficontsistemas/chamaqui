package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	@EntityGraph(attributePaths = {"requester", "assignedTo", "sector", "status", "priority"})
	List<Ticket> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = {"requester", "assignedTo", "sector", "status", "priority"})
	List<Ticket> findByStatusCodeOrderByCreatedAtDesc(String statusCode);
}
