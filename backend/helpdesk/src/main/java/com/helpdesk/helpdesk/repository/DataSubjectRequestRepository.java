package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.DataSubjectRequest;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequest, UUID> {

	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles"})
	List<DataSubjectRequest> findByRequesterUserIdOrderByRequestedAtDesc(UUID requesterUserId);

	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles"})
	List<DataSubjectRequest> findByTenantOwnerUserIdOrderByRequestedAtDesc(UUID tenantOwnerUserId);
}
