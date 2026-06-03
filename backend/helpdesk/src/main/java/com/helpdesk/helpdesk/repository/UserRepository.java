package com.helpdesk.helpdesk.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByDocumentNumber(String documentNumber);

	boolean existsByCompanyDocument(String companyDocument);

	boolean existsByCompanyOwnerIdAndIdNot(UUID companyOwnerId, UUID userId);

	@Query("""
		select case when count(user) > 0 then true else false end
		from User user
		join user.roles role
		where role.code = 'ADMIN'
		  and user.companyDocument = :companyDocument
		""")
	boolean existsAdminCompanyByCompanyDocument(@Param("companyDocument") String companyDocument);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	Optional<User> findByEmailIgnoreCase(String email);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	java.util.List<User> findAllByEmailIgnoreCaseOrderByCreatedAtAsc(String email);

	@Query("""
		select user
		from User user
		join user.roles role
		where role.code = 'ADMIN'
		  and user.companyDocument = :companyDocument
		order by user.createdAt asc
		""")
	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	java.util.List<User> findAdminCompaniesByCompanyDocument(@Param("companyDocument") String companyDocument);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	java.util.List<User> findAllByDocumentNumberOrderByCreatedAtAsc(String documentNumber);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	Optional<User> findByPhoneNumber(String phoneNumber);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	java.util.List<User> findAllByPhoneNumberOrderByCreatedAtAsc(String phoneNumber);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	Optional<User> findByPasswordResetTokenHash(String passwordResetTokenHash);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	Optional<User> findByWhatsappTransportId(String whatsappTransportId);

	@Override
	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	Optional<User> findById(UUID id);

	@Query("""
		select user
		from User user
		where user.deletedAt is null
		  and user.status = com.helpdesk.helpdesk.domain.UserStatus.ACTIVE
		  and (
			user.id = :companyOwnerId
			or user.companyOwner.id = :companyOwnerId
		  )
		order by lower(user.fullName), lower(user.email)
		""")
	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	java.util.List<User> findActiveByCompanyOwnerId(@Param("companyOwnerId") UUID companyOwnerId);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	java.util.List<User> findByCompanyOwnerIdAndIdNotOrderByFullNameAsc(UUID companyOwnerId, UUID excludedUserId);

	@EntityGraph(attributePaths = {"roles", "companyOwner"})
	java.util.List<User> findByCompanyOwnerIdOrIdOrderByCreatedAtAsc(UUID companyOwnerId, UUID userId);

	@EntityGraph(attributePaths = "roles")
	java.util.List<User> findDistinctByRolesCodeInOrderByFullNameAsc(java.util.Collection<String> roleCodes);

	@Query("""
		select user
		from User user
		join user.roles role
		where role.code = 'ADMIN'
		  and user.deletedAt is null
		  and user.status = com.helpdesk.helpdesk.domain.UserStatus.ACTIVE
		  and user.companyName is not null
		  and user.companyDocument is not null
		  and user.companyOwner is null
		  and user.id <> :excludedCompanyId
		  and user.companyType = :companyType
		  and (
			lower(user.companyName) like lower(concat('%', :query, '%'))
			or (:documentQuery <> '' and user.companyDocument like concat('%', :documentQuery, '%'))
		  )
		order by lower(user.companyName), lower(user.fullName)
		""")
	@EntityGraph(attributePaths = {"roles"})
	java.util.List<User> searchAdminCompanies(
		@Param("excludedCompanyId") UUID excludedCompanyId,
		@Param("companyType") CompanyType companyType,
		@Param("query") String query,
		@Param("documentQuery") String documentQuery
	);

	@Query("""
		select user
		from User user
		join user.roles role
		where role.code = 'ADMIN'
		  and user.companyName is not null
		  and user.companyDocument is not null
		  and user.companyOwner is null
		  and user.companyType = :companyType
		order by lower(user.companyName), lower(user.fullName)
		""")
	java.util.List<User> findVisibleCompaniesByType(@Param("companyType") CompanyType companyType);

	@Query("""
		select user
		from User user
		join user.roles role
		where role.code = 'ADMIN'
		  and user.id = :companyOwnerId
		  and user.companyName is not null
		  and user.companyDocument is not null
		  and user.companyType = :companyType
		""")
	Optional<User> findAdminCompanyOwnerByIdAndCompanyType(
		@Param("companyOwnerId") UUID companyOwnerId,
		@Param("companyType") CompanyType companyType
	);

	@Query("""
		select user
		from User user
		join user.roles role
		where role.code = 'ADMIN'
		  and user.id = :companyOwnerId
		  and user.companyName is not null
		  and user.companyDocument is not null
		  and user.companyOwner is null
		  and user.companyType = :companyType
		""")
	Optional<User> findStandaloneAdminCompanyOwnerByIdAndCompanyType(
		@Param("companyOwnerId") UUID companyOwnerId,
		@Param("companyType") CompanyType companyType
	);
}
