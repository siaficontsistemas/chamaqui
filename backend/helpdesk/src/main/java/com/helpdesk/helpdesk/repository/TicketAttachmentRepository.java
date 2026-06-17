package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.helpdesk.helpdesk.domain.TicketAttachment;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

	@EntityGraph(attributePaths = {"uploadedBy", "message"})
	List<TicketAttachment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

	@EntityGraph(attributePaths = {"uploadedBy", "message", "ticket"})
	List<TicketAttachment> findByMessageIdOrderByCreatedAtAsc(UUID messageId);

	@EntityGraph(attributePaths = {"uploadedBy", "message", "ticket"})
	Optional<TicketAttachment> findByIdAndTicketId(UUID id, UUID ticketId);

	@EntityGraph(attributePaths = {"uploadedBy", "message", "ticket"})
	List<TicketAttachment> findByUploadedByIdOrderByCreatedAtAsc(UUID uploadedById);

	@EntityGraph(attributePaths = {"uploadedBy", "message", "ticket"})
	@Query(
		"""
		select attachment
		from TicketAttachment attachment
		where attachment.ticket.requester.id = :userId
			or attachment.ticket.sector.createdBy.id = :userId
		order by attachment.createdAt asc
		"""
	)
	List<TicketAttachment> findManagedForUserCleanup(UUID userId);
}
