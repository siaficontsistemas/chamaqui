package com.helpdesk.helpdesk.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByDocumentNumber(String documentNumber);

	@EntityGraph(attributePaths = "roles")
	Optional<User> findByEmailIgnoreCase(String email);

	@EntityGraph(attributePaths = "roles")
	Optional<User> findById(UUID id);

	@EntityGraph(attributePaths = "roles")
	java.util.List<User> findDistinctByRolesCodeInOrderByFullNameAsc(java.util.Collection<String> roleCodes);
}
