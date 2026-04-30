package com.helpdesk.helpdesk.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.WhatsappConversation;
import com.helpdesk.helpdesk.domain.WhatsappConversationStep;

public interface WhatsappConversationRepository extends JpaRepository<WhatsappConversation, UUID> {

	@EntityGraph(attributePaths = {"companyOwner", "sector", "activeTicket"})
	Optional<WhatsappConversation> findByCompanyOwnerIdAndPhoneNumber(UUID companyOwnerId, String phoneNumber);

	@EntityGraph(attributePaths = {"companyOwner", "sector", "activeTicket"})
	Optional<WhatsappConversation> findByCompanyOwnerIdAndWhatsappTransportId(UUID companyOwnerId, String whatsappTransportId);

	@EntityGraph(attributePaths = {"companyOwner", "sector", "activeTicket"})
	Optional<WhatsappConversation> findByActiveTicketId(UUID activeTicketId);

	@Query("""
		select conversation
		from WhatsappConversation conversation
		where conversation.lastInboundMessageAt is not null
			and conversation.lastInboundMessageAt <= :inactiveSince
			and (
				conversation.currentStep = :activeTicketStep
				or conversation.currentStep = :selectionStep
			)
			and (
				conversation.lastTicketSelectionPromptAt is null
				or conversation.lastTicketSelectionPromptAt <= :promptBefore
			)
		""")
	@EntityGraph(attributePaths = {"companyOwner", "sector", "activeTicket"})
	List<WhatsappConversation> findConversationsPendingTicketSelectionPrompt(
		@Param("inactiveSince") OffsetDateTime inactiveSince,
		@Param("promptBefore") OffsetDateTime promptBefore,
		@Param("activeTicketStep") WhatsappConversationStep activeTicketStep,
		@Param("selectionStep") WhatsappConversationStep selectionStep
	);
}
