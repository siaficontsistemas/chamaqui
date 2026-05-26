package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

	@EntityGraph(attributePaths = {"ownerUser", "ownerUser.roles"})
	@Override
	Optional<Company> findById(UUID id);

	@EntityGraph(attributePaths = {"ownerUser", "ownerUser.roles"})
	Optional<Company> findByOwnerUserId(UUID ownerUserId);

	@EntityGraph(attributePaths = {"ownerUser", "ownerUser.roles"})
	Optional<Company> findBySubdomainIgnoreCaseAndActiveTrue(String subdomain);

	@EntityGraph(attributePaths = {"ownerUser", "ownerUser.roles"})
	List<Company> findAllByOrderByCompanyNameAsc();

	List<Company> findAllByActiveTrueOrderByCompanyNameAsc();

	boolean existsByCompanyDocument(String companyDocument);

	boolean existsBySubdomainIgnoreCase(String subdomain);

	boolean existsBySubdomainIgnoreCaseAndIdNot(String subdomain, UUID id);

	boolean existsBySchemaNameIgnoreCase(String schemaName);

	boolean existsBySchemaNameIgnoreCaseAndIdNot(String schemaName, UUID id);

	void deleteByOwnerUserId(UUID ownerUserId);
}
