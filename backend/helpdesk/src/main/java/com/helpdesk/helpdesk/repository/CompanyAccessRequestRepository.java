package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.CompanyAccessRequest;
import com.helpdesk.helpdesk.domain.CompanyAccessRequestStatus;

public interface CompanyAccessRequestRepository extends JpaRepository<CompanyAccessRequest, UUID> {

	boolean existsByRequesterUserIdAndStatus(UUID requesterUserId, CompanyAccessRequestStatus status);

	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany"})
	List<CompanyAccessRequest> findByTargetCompanyIdAndStatusOrderByCreatedAtDesc(
		UUID targetCompanyId,
		CompanyAccessRequestStatus status
	);

	@Override
	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany", "respondedBy"})
	Optional<CompanyAccessRequest> findById(UUID id);
}
