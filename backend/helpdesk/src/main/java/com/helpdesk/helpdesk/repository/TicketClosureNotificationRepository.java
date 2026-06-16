package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.TicketClosureNotification;

public interface TicketClosureNotificationRepository extends JpaRepository<TicketClosureNotification, UUID> {

	@Query("""
		select notification
		from TicketClosureNotification notification
		where lower(notification.recipient.email) = lower(:email)
			and notification.hidden = false
			and notification.ticket.deletedAt is null
			and upper(notification.ticket.status.code) <> 'CLOSED'
		order by notification.createdAt desc
		""")
	@EntityGraph(attributePaths = {"ticket", "ticket.sector", "ticket.requester", "recipient", "closedBy"})
	List<TicketClosureNotification> findVisibleByRecipientEmailOrderByCreatedAtDesc(@Param("email") String email);

	@EntityGraph(attributePaths = {"ticket", "ticket.sector", "ticket.requester", "recipient", "closedBy"})
	Optional<TicketClosureNotification> findDetailedById(UUID id);
}
