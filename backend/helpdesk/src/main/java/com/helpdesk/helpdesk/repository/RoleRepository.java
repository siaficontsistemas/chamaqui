package com.helpdesk.helpdesk.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {

	Optional<Role> findByCode(String code);
}
