package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.DataSubjectRequest;
import com.helpdesk.helpdesk.domain.DataSubjectRequestStatus;
import com.helpdesk.helpdesk.domain.DataSubjectRightType;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.profile.CreateDataSubjectRequestRequest;
import com.helpdesk.helpdesk.dto.profile.DataSubjectRequestResponse;
import com.helpdesk.helpdesk.dto.profile.ManageDataSubjectRequestRequest;
import com.helpdesk.helpdesk.repository.DataSubjectRequestRepository;

@Service
public class DataSubjectRequestService {

	private final DataSubjectRequestRepository dataSubjectRequestRepository;
	private final TenantAccessService tenantAccessService;
	private final AuditTrailService auditTrailService;
	private final int slaDays;

	public DataSubjectRequestService(
		DataSubjectRequestRepository dataSubjectRequestRepository,
		TenantAccessService tenantAccessService,
		AuditTrailService auditTrailService,
		@Value("${app.privacy.data-subject-request-sla-days:15}") int slaDays
	) {
		this.dataSubjectRequestRepository = dataSubjectRequestRepository;
		this.tenantAccessService = tenantAccessService;
		this.auditTrailService = auditTrailService;
		this.slaDays = Math.max(slaDays, 1);
	}

	@Transactional
	public DataSubjectRequestResponse create(User requester, CreateDataSubjectRequestRequest request) {
		DataSubjectRequest dataSubjectRequest = new DataSubjectRequest();
		dataSubjectRequest.setRequesterUser(requester);
		dataSubjectRequest.setTenantOwnerUserId(resolveTenantOwnerUserId(requester));
		dataSubjectRequest.setRequestType(DataSubjectRightType.valueOf(request.requestType().trim().toUpperCase(Locale.ROOT)));
		dataSubjectRequest.setStatus(DataSubjectRequestStatus.OPEN);
		dataSubjectRequest.setRequesterFullName(requester.getFullName());
		dataSubjectRequest.setRequesterEmail(normalizeEmail(requester.getEmail()));
		dataSubjectRequest.setRequestDescription(request.requestDescription().trim());
		dataSubjectRequest.setRequestedAt(OffsetDateTime.now());
		dataSubjectRequest.setDueAt(dataSubjectRequest.getRequestedAt().plusDays(slaDays));
		DataSubjectRequest savedRequest = dataSubjectRequestRepository.save(dataSubjectRequest);
		auditTrailService.recordUserAction(
			"DATA_SUBJECT_REQUEST_CREATED",
			requester,
			"data-subject-request",
			savedRequest.getId()
		);
		return toResponse(savedRequest, false);
	}

	@Transactional(readOnly = true)
	public List<DataSubjectRequestResponse> listMine(User requester) {
		return dataSubjectRequestRepository.findByRequesterUserIdOrderByRequestedAtDesc(requester.getId()).stream()
			.map(request -> toResponse(request, false))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<DataSubjectRequestResponse> listForAdmin(User admin) {
		ensureAdmin(admin);
		UUID tenantOwnerUserId = resolveTenantOwnerUserId(admin);
		if (tenantOwnerUserId == null) {
			return List.of();
		}
		return dataSubjectRequestRepository.findByTenantOwnerUserIdOrderByRequestedAtDesc(tenantOwnerUserId).stream()
			.map(request -> toResponse(request, true))
			.toList();
	}

	@Transactional
	public DataSubjectRequestResponse manage(User admin, UUID requestId, ManageDataSubjectRequestRequest request) {
		ensureAdmin(admin);
		DataSubjectRequest dataSubjectRequest = dataSubjectRequestRepository.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Solicitação do titular não encontrada."));
		UUID tenantOwnerUserId = resolveTenantOwnerUserId(admin);
		if (tenantOwnerUserId == null || !tenantOwnerUserId.equals(dataSubjectRequest.getTenantOwnerUserId())) {
			throw new IllegalArgumentException("Essa solicitação não pertence ao ambiente administrativo atual.");
		}

		DataSubjectRequestStatus nextStatus = DataSubjectRequestStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
		dataSubjectRequest.setStatus(nextStatus);
		dataSubjectRequest.setResponseSummary(blankToNull(request.responseSummary()));
		dataSubjectRequest.setInternalNotes(blankToNull(request.internalNotes()));
		if (nextStatus == DataSubjectRequestStatus.COMPLETED || nextStatus == DataSubjectRequestStatus.REJECTED) {
			dataSubjectRequest.setResolvedAt(OffsetDateTime.now());
		} else {
			dataSubjectRequest.setResolvedAt(null);
		}

		DataSubjectRequest savedRequest = dataSubjectRequestRepository.save(dataSubjectRequest);
		auditTrailService.recordUserAction(
			"DATA_SUBJECT_REQUEST_MANAGED",
			admin,
			"data-subject-request",
			savedRequest.getId()
		);
		return toResponse(savedRequest, true);
	}

	private DataSubjectRequestResponse toResponse(DataSubjectRequest request, boolean includeInternalNotes) {
		return new DataSubjectRequestResponse(
			request.getId(),
			request.getRequestType().name(),
			request.getStatus().name(),
			request.getRequesterFullName(),
			request.getRequesterEmail(),
			request.getRequestDescription(),
			request.getResponseSummary(),
			includeInternalNotes ? request.getInternalNotes() : null,
			request.getRequestedAt(),
			request.getDueAt(),
			request.getResolvedAt()
		);
	}

	private UUID resolveTenantOwnerUserId(User user) {
		return tenantAccessService.findPrimaryCompanyForUser(user)
			.map(company -> company.getOwnerUser() == null ? null : company.getOwnerUser().getId())
			.orElse(user.getCompanyOwner() == null ? user.getId() : user.getCompanyOwner().getId());
	}

	private void ensureAdmin(User admin) {
		if (admin == null || admin.getRoles().stream().noneMatch(role -> "ADMIN".equalsIgnoreCase(role.getCode()))) {
			throw new IllegalArgumentException("Somente administradores podem gerenciar solicitações do titular.");
		}
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
