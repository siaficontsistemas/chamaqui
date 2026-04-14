package com.helpdesk.helpdesk.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.SectorMember;

public interface SectorMemberRepository extends JpaRepository<SectorMember, UUID> {

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findAllByOrderByAssignedAtAsc();

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findByUserIdOrderByAssignedAtAsc(UUID userId);

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findBySectorIdOrderByAssignedAtAsc(UUID sectorId);

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findBySectorIdInOrderByAssignedAtAsc(Collection<UUID> sectorIds);

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	Optional<SectorMember> findByUserIdAndSectorId(UUID userId, UUID sectorId);

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findBySectorCreatedByIdOrderByAssignedAtAsc(UUID createdById);

	@Query("""
		select member
		from SectorMember member
		join member.user user
		join user.roles role
		where member.sector.createdBy.id = :companyOwnerId
			and role.code = 'EMPLOYEE'
			and user.status = com.helpdesk.helpdesk.domain.UserStatus.ACTIVE
		""")
	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findActiveEmployeesByCompanyOwnerIdOrderByUserFullNameAsc(
		@Param("companyOwnerId") UUID companyOwnerId
	);

	void deleteByUserId(UUID userId);
}
