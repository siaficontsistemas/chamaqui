package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.TicketReplyNotification;

public interface TicketReplyNotificationRepository extends JpaRepository<TicketReplyNotification, UUID> {

	@Query("""
		select notification
		from TicketReplyNotification notification
		where lower(notification.recipient.email) = lower(:email)
			and notification.hidden = false
		order by notification.createdAt desc
		""")
	@EntityGraph(attributePaths = {"ticket", "ticket.requester", "ticket.sector", "message", "recipient"})
	List<TicketReplyNotification> findVisibleByRecipientEmailOrderByCreatedAtDesc(@Param("email") String email);

	@EntityGraph(attributePaths = {"ticket", "ticket.requester", "ticket.sector", "message", "recipient"})
	Optional<TicketReplyNotification> findDetailedById(UUID id);

	@EntityGraph(attributePaths = {"ticket", "ticket.requester", "ticket.sector", "message", "recipient"})
	List<TicketReplyNotification> findByTicketId(UUID ticketId);
}
