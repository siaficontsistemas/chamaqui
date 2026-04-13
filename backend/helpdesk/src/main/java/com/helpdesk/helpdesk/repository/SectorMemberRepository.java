package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.SectorMember;

public interface SectorMemberRepository extends JpaRepository<SectorMember, UUID> {

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findAllByOrderByAssignedAtAsc();

	@EntityGraph(attributePaths = {"sector", "user", "user.roles", "assignedBy"})
	List<SectorMember> findByUserIdOrderByAssignedAtAsc(UUID userId);

	void deleteByUserId(UUID userId);
}
