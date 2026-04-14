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
		where lower(ticket.requester.email) = lower(:email)
			or lower(ticket.sector.createdBy.email) = lower(:email)
			or lower(ticket.assignedTo.email) = lower(:email)
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
		where ticket.status.code in :statusCodes
			and (
				lower(ticket.requester.email) = lower(:email)
				or lower(ticket.sector.createdBy.email) = lower(:email)
				or lower(ticket.assignedTo.email) = lower(:email)
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
		where ticket.id = :id
			and (
				lower(ticket.requester.email) = lower(:email)
				or lower(ticket.sector.createdBy.email) = lower(:email)
				or lower(ticket.assignedTo.email) = lower(:email)
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
}
