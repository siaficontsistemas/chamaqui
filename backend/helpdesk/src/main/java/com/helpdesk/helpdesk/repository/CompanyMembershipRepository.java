package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.CompanyMembership;
import com.helpdesk.helpdesk.domain.CompanyType;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {

	boolean existsByUserIdAndCompanyOwnerId(UUID userId, UUID companyOwnerId);

	@EntityGraph(attributePaths = {"user", "user.roles", "companyOwner", "companyOwner.roles"})
	List<CompanyMembership> findByCompanyOwnerIdOrderByJoinedAtAsc(UUID companyOwnerId);

	@EntityGraph(attributePaths = {"user", "user.roles", "companyOwner", "companyOwner.roles", "companyOwner.companyOwner"})
	List<CompanyMembership> findByCompanyOwnerCompanyOwnerIdOrderByJoinedAtAsc(UUID companyOwnerOwnerId);

	@EntityGraph(attributePaths = {"user", "user.roles", "companyOwner", "companyOwner.roles"})
	List<CompanyMembership> findByUserIdOrderByJoinedAtAsc(UUID userId);

	@EntityGraph(attributePaths = {"user", "user.roles", "companyOwner", "companyOwner.roles"})
	Optional<CompanyMembership> findByUserIdAndCompanyOwnerId(UUID userId, UUID companyOwnerId);

	void deleteByUserIdAndCompanyOwnerId(UUID userId, UUID companyOwnerId);

	void deleteByCompanyOwnerId(UUID companyOwnerId);

	@Query("""
		select case when count(membership) > 0 then true else false end
		from CompanyMembership membership
		where membership.user.id = :userId
		  and membership.companyOwner.companyType = :companyType
		""")
	boolean existsByUserIdAndCompanyType(
		@Param("userId") UUID userId,
		@Param("companyType") CompanyType companyType
	);

	@Query("""
		select case when count(membership) > 0 then true else false end
		from CompanyMembership membership
		where membership.user.id = :userId
		  and membership.companyOwner.companyOwner.id = :companyOwnerId
		  and membership.companyOwner.companyType = :companyType
		""")
	boolean existsByUserIdAndNestedCompanyOwnerIdAndCompanyType(
		@Param("userId") UUID userId,
		@Param("companyOwnerId") UUID companyOwnerId,
		@Param("companyType") CompanyType companyType
	);
}
