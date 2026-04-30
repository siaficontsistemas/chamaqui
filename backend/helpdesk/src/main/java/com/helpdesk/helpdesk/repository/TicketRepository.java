package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	@Query("""
		select distinct ticket
		from Ticket ticket
		left join ticket.requester requester
		left join requester.companyOwner requesterCompanyOwner
		left join ticket.sector sector
		left join sector.createdBy sectorOwner
		left join ticket.assignedTo assignedTo
		where lower(requester.email) = lower(:email)
			or lower(requesterCompanyOwner.email) = lower(:email)
			or lower(sectorOwner.email) = lower(:email)
			or lower(assignedTo.email) = lower(:email)
		order by ticket.createdAt desc
		""")
	@EntityGraph(attributePaths = {
		"requester",
		"assignedTo",
		"pendingTransferTo",
		"pendingTransferRequestedBy",
		"sector",
		"status",
		"priority"
	})
	List<Ticket> findVisibleByEmailOrderByCreatedAtDesc(@Param("email") String email);

	@Query("""
		select distinct ticket
		from Ticket ticket
		left join ticket.requester requester
		left join requester.companyOwner requesterCompanyOwner
		left join ticket.sector sector
		left join sector.createdBy sectorOwner
		left join ticket.assignedTo assignedTo
		where ticket.status.code in :statusCodes
			and (
				lower(requester.email) = lower(:email)
				or lower(requesterCompanyOwner.email) = lower(:email)
				or lower(sectorOwner.email) = lower(:email)
				or lower(assignedTo.email) = lower(:email)
			)
		order by ticket.createdAt desc
		""")
	@EntityGraph(attributePaths = {
		"requester",
		"assignedTo",
		"pendingTransferTo",
		"pendingTransferRequestedBy",
		"sector",
		"status",
		"priority"
	})
	List<Ticket> findVisibleByEmailAndStatusCodesOrderByCreatedAtDesc(
		@Param("email") String email,
		@Param("statusCodes") List<String> statusCodes
	);

	@Query("""
		select distinct ticket
		from Ticket ticket
		left join ticket.requester requester
		left join requester.companyOwner requesterCompanyOwner
		left join ticket.sector sector
		left join sector.createdBy sectorOwner
		left join ticket.assignedTo assignedTo
		where ticket.id = :id
			and (
				lower(requester.email) = lower(:email)
				or lower(requesterCompanyOwner.email) = lower(:email)
				or lower(sectorOwner.email) = lower(:email)
				or lower(assignedTo.email) = lower(:email)
			)
		""")
	@EntityGraph(attributePaths = {
		"requester",
		"requester.roles",
		"assignedTo",
		"pendingTransferTo",
		"pendingTransferRequestedBy",
		"sector",
		"sector.createdBy",
		"status",
		"priority"
	})
	Optional<Ticket> findDetailedVisibleByIdAndEmail(@Param("id") UUID id, @Param("email") String email);

	@EntityGraph(attributePaths = {"assignedTo"})
	Optional<Ticket> findFirstBySectorIdAndAssignedToIdInOrderByCreatedAtDesc(UUID sectorId, List<UUID> assignedToIds);

	@Query("""
		select distinct ticket
		from Ticket ticket
		where ticket.assignedTo.id = :userId
			or ticket.pendingTransferTo.id = :userId
			or ticket.pendingTransferRequestedBy.id = :userId
		""")
	@EntityGraph(attributePaths = {
		"assignedTo",
		"pendingTransferTo",
		"pendingTransferRequestedBy",
		"sector"
	})
	List<Ticket> findTicketsAffectedByUserId(@Param("userId") UUID userId);

	@Query("""
		select distinct ticket
		from Ticket ticket
		where ticket.sector.id in :sectorIds
			and (
				ticket.assignedTo.id = :userId
				or ticket.pendingTransferTo.id = :userId
				or ticket.pendingTransferRequestedBy.id = :userId
			)
		""")
	@EntityGraph(attributePaths = {
		"assignedTo",
		"pendingTransferTo",
		"pendingTransferRequestedBy",
		"sector"
	})
	List<Ticket> findTicketsAffectedByUserIdAndSectorIdIn(
		@Param("userId") UUID userId,
		@Param("sectorIds") List<UUID> sectorIds
	);

	@Query("""
		select distinct ticket
		from Ticket ticket
		join ticket.requester requester
		join ticket.sector sector
		join ticket.status status
		where sector.createdBy.id = :companyOwnerId
			and ticket.deletedAt is null
			and ticket.closedAt is null
			and upper(status.code) <> 'CLOSED'
			and (
				(:requesterId is not null and requester.id = :requesterId)
				or (:email <> '' and lower(requester.email) = lower(:email))
				or (:phoneNumber <> '' and requester.phoneNumber = :phoneNumber)
				or (:whatsappTransportId <> '' and requester.whatsappTransportId = :whatsappTransportId)
			)
		order by ticket.createdAt desc
		""")
	@EntityGraph(attributePaths = {
		"requester",
		"assignedTo",
		"sector",
		"status"
	})
	List<Ticket> findOpenWhatsappTicketsForRouting(
		@Param("companyOwnerId") UUID companyOwnerId,
		@Param("requesterId") UUID requesterId,
		@Param("email") String email,
		@Param("phoneNumber") String phoneNumber,
		@Param("whatsappTransportId") String whatsappTransportId
	);
}
