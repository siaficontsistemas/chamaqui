package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.CalendarReminderNotification;

public interface CalendarReminderNotificationRepository extends JpaRepository<CalendarReminderNotification, UUID> {

	@Query("""
		select notification
		from CalendarReminderNotification notification
		where lower(notification.recipient.email) = lower(:email)
			and notification.hidden = false
		order by notification.createdAt desc
		""")
	@EntityGraph(attributePaths = {
		"recipient",
		"obligation",
		"obligation.companyOwner",
		"obligation.createdBy",
	})
	List<CalendarReminderNotification> findVisibleByRecipientEmailOrderByCreatedAtDesc(@Param("email") String email);

	@EntityGraph(attributePaths = {
		"recipient",
		"obligation",
		"obligation.companyOwner",
		"obligation.createdBy",
	})
	Optional<CalendarReminderNotification> findDetailedById(UUID id);

	Optional<CalendarReminderNotification> findByObligationIdAndRecipientId(UUID obligationId, UUID recipientId);

	boolean existsByObligationIdAndRecipientId(UUID obligationId, UUID recipientId);

	void deleteByObligationId(UUID obligationId);
}
