package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.InviteStatus;
import com.helpdesk.helpdesk.domain.TeamInvite;

public interface TeamInviteRepository extends JpaRepository<TeamInvite, UUID> {

	@EntityGraph(attributePaths = {"invitedBy", "acceptedUser", "sectors"})
	List<TeamInvite> findAllByOrderByCreatedAtDesc();

	@EntityGraph(attributePaths = {"invitedBy", "acceptedUser", "sectors"})
	List<TeamInvite> findAllByEmailIgnoreCaseAndInviteeHiddenFalseOrderByCreatedAtDesc(String email);

	@EntityGraph(attributePaths = {"invitedBy", "acceptedUser", "sectors"})
	List<TeamInvite> findAllByInvitedByEmailIgnoreCaseAndInviterHiddenFalseOrderByCreatedAtDesc(String invitedByEmail);

	@EntityGraph(attributePaths = {"invitedBy", "acceptedUser", "sectors"})
	Optional<TeamInvite> findWithDetailsById(UUID id);

	@EntityGraph(attributePaths = {"invitedBy", "acceptedUser", "sectors"})
	List<TeamInvite> findAllBySectorsId(UUID sectorId);

	boolean existsByEmailIgnoreCaseAndStatusAndInviteeHiddenFalse(String email, InviteStatus status);
}
