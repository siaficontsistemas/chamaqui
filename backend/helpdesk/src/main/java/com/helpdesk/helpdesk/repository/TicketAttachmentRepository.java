package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.TicketAttachment;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

	@EntityGraph(attributePaths = {"uploadedBy", "message"})
	List<TicketAttachment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

	@EntityGraph(attributePaths = {"uploadedBy", "message", "ticket"})
	List<TicketAttachment> findByMessageIdOrderByCreatedAtAsc(UUID messageId);

	@EntityGraph(attributePaths = {"uploadedBy", "message", "ticket"})
	Optional<TicketAttachment> findByIdAndTicketId(UUID id, UUID ticketId);
}
