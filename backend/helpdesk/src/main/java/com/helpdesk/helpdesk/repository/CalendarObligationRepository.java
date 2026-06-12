package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.CalendarObligation;

public interface CalendarObligationRepository extends JpaRepository<CalendarObligation, UUID> {

	@Query("""
		select obligation
		from CalendarObligation obligation
		where obligation.companyOwner.id = :companyOwnerId
		order by
			case when obligation.completedAt is null then 0 else 1 end,
			obligation.dueAt asc,
			obligation.createdAt desc
		""")
	@EntityGraph(attributePaths = {"companyOwner", "createdBy", "recipients", "linkedCompanyOwner", "linkedTickets", "linkedTickets.status", "linkedTickets.assignedTo"})
	List<CalendarObligation> findVisibleByCompanyOwnerIdOrderByDueAtAsc(@Param("companyOwnerId") UUID companyOwnerId);

	@Query("""
		select obligation
		from CalendarObligation obligation
		join obligation.recipients recipient
		where recipient.id = :recipientId
		order by
			case when obligation.completedAt is null then 0 else 1 end,
			obligation.dueAt asc,
			obligation.createdAt desc
		""")
	@EntityGraph(attributePaths = {"companyOwner", "createdBy", "recipients", "linkedCompanyOwner", "linkedTickets", "linkedTickets.status", "linkedTickets.assignedTo"})
	List<CalendarObligation> findVisibleByRecipientIdOrderByDueAtAsc(@Param("recipientId") UUID recipientId);

	@EntityGraph(attributePaths = {"companyOwner", "createdBy", "recipients", "linkedCompanyOwner", "linkedTickets", "linkedTickets.status", "linkedTickets.assignedTo"})
	Optional<CalendarObligation> findDetailedById(UUID id);
}
