package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.TicketTransferNotification;
import com.helpdesk.helpdesk.domain.TicketTransferStatus;

public interface TicketTransferNotificationRepository extends JpaRepository<TicketTransferNotification, UUID> {

	@Query("""
		select notification
		from TicketTransferNotification notification
		where lower(notification.recipient.email) = lower(:email)
			and notification.hidden = false
			and notification.ticket.deletedAt is null
			and upper(notification.ticket.status.code) <> 'CLOSED'
		order by notification.createdAt desc
		""")
	@EntityGraph(attributePaths = {"ticket", "ticket.requester", "ticket.sector", "sender", "recipient"})
	List<TicketTransferNotification> findVisibleByRecipientEmailOrderByCreatedAtDesc(@Param("email") String email);

	@EntityGraph(attributePaths = {"ticket", "ticket.requester", "ticket.sector", "sender", "recipient"})
	Optional<TicketTransferNotification> findDetailedById(UUID id);

	@EntityGraph(attributePaths = {"ticket", "ticket.requester", "ticket.sector", "sender", "recipient"})
	List<TicketTransferNotification> findByTicketIdAndStatus(UUID ticketId, TicketTransferStatus status);

	@EntityGraph(attributePaths = {"ticket", "ticket.requester", "ticket.sector", "sender", "recipient"})
	List<TicketTransferNotification> findByTicketId(UUID ticketId);

	boolean existsByTicketIdAndStatus(UUID ticketId, TicketTransferStatus status);
}
