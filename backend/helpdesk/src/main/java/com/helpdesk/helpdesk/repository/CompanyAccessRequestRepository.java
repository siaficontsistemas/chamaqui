package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.helpdesk.helpdesk.domain.CompanyAccessRequest;
import com.helpdesk.helpdesk.domain.CompanyAccessRequestStatus;
import com.helpdesk.helpdesk.domain.CompanyAccessRequestType;

public interface CompanyAccessRequestRepository extends JpaRepository<CompanyAccessRequest, UUID> {

	boolean existsByRequesterUserIdAndStatus(UUID requesterUserId, CompanyAccessRequestStatus status);

	boolean existsByRequesterUserIdAndRequestTypeAndStatus(
		UUID requesterUserId,
		CompanyAccessRequestType requestType,
		CompanyAccessRequestStatus status
	);

	boolean existsByTargetCompanyIdAndRequesterEmailIgnoreCaseAndRequestTypeAndStatus(
		UUID targetCompanyId,
		String requesterEmail,
		CompanyAccessRequestType requestType,
		CompanyAccessRequestStatus status
	);

	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany"})
	List<CompanyAccessRequest> findByTargetCompanyIdAndRequestTypeAndStatusOrderByCreatedAtDesc(
		UUID targetCompanyId,
		CompanyAccessRequestType requestType,
		CompanyAccessRequestStatus status
	);

	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany", "respondedBy"})
	List<CompanyAccessRequest> findByRequesterUserIdAndRequestTypeAndStatusOrderByCreatedAtDesc(
		UUID requesterUserId,
		CompanyAccessRequestType requestType,
		CompanyAccessRequestStatus status
	);

	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany", "respondedBy"})
	Optional<CompanyAccessRequest> findByInviteTokenHashAndRequestTypeAndStatus(
		String inviteTokenHash,
		CompanyAccessRequestType requestType,
		CompanyAccessRequestStatus status
	);

	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany", "respondedBy"})
	List<CompanyAccessRequest> findByTargetCompanyIdAndRequesterEmailIgnoreCaseAndRequestTypeAndStatusOrderByCreatedAtDesc(
		UUID targetCompanyId,
		String requesterEmail,
		CompanyAccessRequestType requestType,
		CompanyAccessRequestStatus status
	);

	@org.springframework.data.jpa.repository.Query("""
		select request
		from CompanyAccessRequest request
		where request.requestType = :requestType
		  and request.status = :status
		  and request.requesterUser is null
		  and (
			lower(request.requesterEmail) = lower(:requesterEmail)
			or (:requesterDocumentNumber <> '' and request.requesterDocumentNumber = :requesterDocumentNumber)
		  )
		order by request.createdAt desc
		""")
	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany", "respondedBy"})
	List<CompanyAccessRequest> findPendingAdminInvitesForIdentity(
		@org.springframework.data.repository.query.Param("requestType") CompanyAccessRequestType requestType,
		@org.springframework.data.repository.query.Param("status") CompanyAccessRequestStatus status,
		@org.springframework.data.repository.query.Param("requesterEmail") String requesterEmail,
		@org.springframework.data.repository.query.Param("requesterDocumentNumber") String requesterDocumentNumber
	);

	@Override
	@EntityGraph(attributePaths = {"requesterUser", "requesterUser.roles", "targetCompany", "respondedBy"})
	Optional<CompanyAccessRequest> findById(UUID id);
}
