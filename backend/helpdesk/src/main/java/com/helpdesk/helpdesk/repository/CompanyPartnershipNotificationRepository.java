package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.CompanyPartnershipNotification;
import com.helpdesk.helpdesk.domain.CompanyPartnershipNotificationType;

public interface CompanyPartnershipNotificationRepository extends JpaRepository<CompanyPartnershipNotification, UUID> {

	@Query("""
		select notification
		from CompanyPartnershipNotification notification
		where lower(notification.recipient.email) = lower(:email)
			and notification.hidden = false
		order by notification.createdAt desc
		""")
	@EntityGraph(attributePaths = {"recipient", "actorUser"})
	List<CompanyPartnershipNotification> findVisibleByRecipientEmailOrderByCreatedAtDesc(@Param("email") String email);

	@EntityGraph(attributePaths = {"recipient", "actorUser"})
	Optional<CompanyPartnershipNotification> findDetailedById(UUID id);

	@EntityGraph(attributePaths = {"recipient", "actorUser"})
	List<CompanyPartnershipNotification> findByCompanyPartnershipIdAndRecipientIdAndTypeAndHiddenFalse(
		UUID companyPartnershipId,
		UUID recipientId,
		CompanyPartnershipNotificationType type
	);
}
