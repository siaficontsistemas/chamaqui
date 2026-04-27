package com.helpdesk.helpdesk.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.CompanyPartnership;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;

public interface CompanyPartnershipRepository extends JpaRepository<CompanyPartnership, UUID> {

	@Override
	@EntityGraph(attributePaths = {"requesterCompany", "targetCompany", "requestedBy", "respondedBy"})
	Optional<CompanyPartnership> findById(UUID id);

	@Query("""
		select partnership
		from CompanyPartnership partnership
		where partnership.requesterCompany.id = :companyId
		   or partnership.targetCompany.id = :companyId
		order by partnership.createdAt desc
		""")
	@EntityGraph(attributePaths = {"requesterCompany", "targetCompany", "requestedBy", "respondedBy"})
	List<CompanyPartnership> findVisibleByCompanyId(@Param("companyId") UUID companyId);

	@Query("""
		select partnership
		from CompanyPartnership partnership
		where (
			(partnership.requesterCompany.id = :firstCompanyId and partnership.targetCompany.id = :secondCompanyId)
			or (partnership.requesterCompany.id = :secondCompanyId and partnership.targetCompany.id = :firstCompanyId)
		)
		and partnership.status in :statuses
		order by partnership.createdAt desc
		""")
	@EntityGraph(attributePaths = {"requesterCompany", "targetCompany", "requestedBy", "respondedBy"})
	List<CompanyPartnership> findByCompanyPairAndStatuses(
		@Param("firstCompanyId") UUID firstCompanyId,
		@Param("secondCompanyId") UUID secondCompanyId,
		@Param("statuses") Collection<CompanyPartnershipStatus> statuses
	);

	@Query("""
		select case when count(partnership) > 0 then true else false end
		from CompanyPartnership partnership
		where (
			(partnership.requesterCompany.id = :firstCompanyId and partnership.targetCompany.id = :secondCompanyId)
			or (partnership.requesterCompany.id = :secondCompanyId and partnership.targetCompany.id = :firstCompanyId)
		)
		and partnership.status = :status
		""")
	boolean existsByCompanyPairAndStatus(
		@Param("firstCompanyId") UUID firstCompanyId,
		@Param("secondCompanyId") UUID secondCompanyId,
		@Param("status") CompanyPartnershipStatus status
	);
}
