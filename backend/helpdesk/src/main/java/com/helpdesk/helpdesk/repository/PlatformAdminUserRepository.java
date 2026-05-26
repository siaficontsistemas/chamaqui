package com.helpdesk.helpdesk.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.PlatformAdminUser;

public interface PlatformAdminUserRepository extends JpaRepository<PlatformAdminUser, UUID> {

	Optional<PlatformAdminUser> findByEmailIgnoreCase(String email);
}
