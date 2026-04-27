package com.helpdesk.helpdesk.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.WhatsappConversation;

public interface WhatsappConversationRepository extends JpaRepository<WhatsappConversation, UUID> {

	@EntityGraph(attributePaths = {"companyOwner", "sector", "activeTicket"})
	Optional<WhatsappConversation> findByCompanyOwnerIdAndPhoneNumber(UUID companyOwnerId, String phoneNumber);

	@EntityGraph(attributePaths = {"companyOwner", "sector", "activeTicket"})
	Optional<WhatsappConversation> findByCompanyOwnerIdAndWhatsappTransportId(UUID companyOwnerId, String whatsappTransportId);

	@EntityGraph(attributePaths = {"companyOwner", "sector", "activeTicket"})
	Optional<WhatsappConversation> findByActiveTicketId(UUID activeTicketId);
}
