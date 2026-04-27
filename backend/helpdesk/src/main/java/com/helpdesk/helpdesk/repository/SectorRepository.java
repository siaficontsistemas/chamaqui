package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Sector;

public interface SectorRepository extends JpaRepository<Sector, UUID> {

	@EntityGraph(attributePaths = { "createdBy", "members" })
	List<Sector> findByArchivedAtIsNullOrderByNameAsc();

	@Query("""
		select distinct sector
		from Sector sector
		left join sector.members member
		left join member.user memberUser
		where sector.archivedAt is null
			and (
				lower(sector.createdBy.email) = lower(:email)
				or lower(memberUser.email) = lower(:email)
			)
		order by sector.name asc
		""")
	@EntityGraph(attributePaths = { "createdBy", "members" })
	List<Sector> findVisibleToAdminByEmail(@Param("email") String email);

	@Query("""
		select distinct sector
		from Sector sector
		join sector.members member
		join member.user memberUser
		where sector.archivedAt is null
			and lower(memberUser.email) = lower(:email)
		order by sector.name asc
		""")
	@EntityGraph(attributePaths = { "createdBy", "members" })
	List<Sector> findVisibleToMemberByEmail(@Param("email") String email);

	@Query("""
		select distinct sector
		from Sector sector
		where sector.archivedAt is null
			and sector.createdBy.companyType = :companyType
		order by sector.name asc
		""")
	@EntityGraph(attributePaths = { "createdBy", "members" })
	List<Sector> findVisibleByCompanyType(@Param("companyType") CompanyType companyType);

	@Query("""
		select distinct sector
		from Sector sector
		where sector.archivedAt is null
			and sector.active = true
			and sector.createdBy.id = :companyOwnerId
		order by sector.name asc
		""")
	@EntityGraph(attributePaths = { "createdBy", "members" })
	List<Sector> findActiveByCreatedByIdOrderByNameAsc(@Param("companyOwnerId") UUID companyOwnerId);

	@Query("""
		select distinct sector
		from Sector sector
		where sector.archivedAt is null
			and sector.active = true
			and sector.createdBy.id in :companyOwnerIds
		order by sector.name asc
		""")
	@EntityGraph(attributePaths = { "createdBy", "members" })
	List<Sector> findActiveByCreatedByIdInOrderByNameAsc(@Param("companyOwnerIds") java.util.Collection<UUID> companyOwnerIds);

	boolean existsBySlug(String slug);

	Optional<Sector> findBySlug(String slug);
}
