package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.TicketMessage;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

	@EntityGraph(attributePaths = {"author", "author.roles", "ticket"})
	List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

	@EntityGraph(attributePaths = {"author", "ticket", "ticket.requester", "ticket.requester.companyOwner"})
	List<TicketMessage> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

	@EntityGraph(attributePaths = {"author", "author.roles", "ticket"})
	Optional<TicketMessage> findFirstByTicketIdOrderByCreatedAtAsc(UUID ticketId);

	@EntityGraph(attributePaths = {"author", "author.roles", "ticket"})
	Optional<TicketMessage> findFirstByTicketIdOrderByCreatedAtDesc(UUID ticketId);

	boolean existsByTicketId(UUID ticketId);

	long countByTicketId(UUID ticketId);
}
