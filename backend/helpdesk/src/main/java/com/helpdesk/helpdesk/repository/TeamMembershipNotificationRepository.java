package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.TeamMembershipNotification;

public interface TeamMembershipNotificationRepository extends JpaRepository<TeamMembershipNotification, UUID> {

	@Query("""
		select notification
		from TeamMembershipNotification notification
		where lower(notification.recipient.email) = lower(:email)
			and notification.hidden = false
		order by notification.createdAt desc
		""")
	@EntityGraph(attributePaths = {"recipient", "removedBy", "removedBy.roles", "sector", "sector.createdBy"})
	List<TeamMembershipNotification> findVisibleByRecipientEmailOrderByCreatedAtDesc(@Param("email") String email);

	@EntityGraph(attributePaths = {"recipient", "removedBy", "removedBy.roles", "sector", "sector.createdBy"})
	Optional<TeamMembershipNotification> findDetailedById(UUID id);

	@Query("""
		select notification
		from TeamMembershipNotification notification
		where notification.id = :id
			and lower(notification.recipient.email) = lower(:email)
		""")
	@EntityGraph(attributePaths = {"recipient", "removedBy", "removedBy.roles", "sector", "sector.createdBy"})
	Optional<TeamMembershipNotification> findDetailedByIdAndRecipientEmail(
		@Param("id") UUID id,
		@Param("email") String email
	);
}
