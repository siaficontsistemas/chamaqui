package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyAccessRequest;
import com.helpdesk.helpdesk.domain.CompanyAccessRequestStatus;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.company.RespondCompanyAccessRequest;
import com.helpdesk.helpdesk.dto.notification.CompanyAccessRequestNotificationResponse;
import com.helpdesk.helpdesk.repository.CompanyAccessRequestRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class CompanyAccessRequestService {

	private final CompanyAccessRequestRepository companyAccessRequestRepository;
	private final UserRepository userRepository;

	public CompanyAccessRequestService(
		CompanyAccessRequestRepository companyAccessRequestRepository,
		UserRepository userRepository
	) {
		this.companyAccessRequestRepository = companyAccessRequestRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public void createPendingRequest(User requesterUser, User targetCompany) {
		if (companyAccessRequestRepository.existsByRequesterUserIdAndStatus(
			requesterUser.getId(),
			CompanyAccessRequestStatus.PENDING
		)) {
			throw new IllegalArgumentException("Já existe uma solicitação pendente aguardando aprovação do administrador.");
		}

		CompanyAccessRequest request = new CompanyAccessRequest();
		request.setRequesterUser(requesterUser);
		request.setTargetCompany(targetCompany);
		request.setStatus(CompanyAccessRequestStatus.PENDING);
		companyAccessRequestRepository.save(request);
	}

	@Transactional(readOnly = true)
	public List<CompanyAccessRequestNotificationResponse> listPendingNotifications(String email) {
		User admin = loadAdminByEmail(email);
		return companyAccessRequestRepository.findByTargetCompanyIdAndStatusOrderByCreatedAtDesc(
			admin.getId(),
			CompanyAccessRequestStatus.PENDING
		)
			.stream()
			.map(this::toNotificationResponse)
			.toList();
	}

	@Transactional
	public void accept(UUID requestId, RespondCompanyAccessRequest request) {
		User admin = loadAdminByEmail(request.email());
		CompanyAccessRequest accessRequest = loadPendingRequestForResponse(requestId, admin);
		User requester = accessRequest.getRequesterUser();

		requester.setCompanyOwner(admin);
		requester.setStatus(UserStatus.ACTIVE);
		userRepository.save(requester);

		accessRequest.setStatus(CompanyAccessRequestStatus.APPROVED);
		accessRequest.setRespondedBy(admin);
		accessRequest.setRespondedAt(OffsetDateTime.now());
		companyAccessRequestRepository.save(accessRequest);
	}

	@Transactional
	public void decline(UUID requestId, RespondCompanyAccessRequest request) {
		User admin = loadAdminByEmail(request.email());
		CompanyAccessRequest accessRequest = loadPendingRequestForResponse(requestId, admin);
		User requester = accessRequest.getRequesterUser();

		requester.setCompanyOwner(null);
		requester.setStatus(UserStatus.INACTIVE);
		userRepository.save(requester);

		accessRequest.setStatus(CompanyAccessRequestStatus.DECLINED);
		accessRequest.setRespondedBy(admin);
		accessRequest.setRespondedAt(OffsetDateTime.now());
		companyAccessRequestRepository.save(accessRequest);
	}

	private CompanyAccessRequestNotificationResponse toNotificationResponse(CompanyAccessRequest request) {
		return new CompanyAccessRequestNotificationResponse(
			request.getId(),
			request.getRequesterUser().getId(),
			request.getRequesterUser().getFullName(),
			request.getRequesterUser().getEmail(),
			request.getRequesterUser().getDocumentNumber(),
			resolvePrimaryRole(request.getRequesterUser()),
			request.getTargetCompany().getCompanyName(),
			request.getTargetCompany().getCompanyType() == null ? null : request.getTargetCompany().getCompanyType().name(),
			request.getStatus().name(),
			request.getCreatedAt()
		);
	}

	private CompanyAccessRequest loadPendingRequestForResponse(UUID requestId, User admin) {
		CompanyAccessRequest accessRequest = companyAccessRequestRepository.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Solicitação de acesso não encontrada."));

		if (!accessRequest.getTargetCompany().getId().equals(admin.getId())) {
			throw new IllegalArgumentException("Somente o administrador da empresa selecionada pode responder a solicitação.");
		}

		if (accessRequest.getStatus() != CompanyAccessRequestStatus.PENDING) {
			throw new IllegalArgumentException("Essa solicitação de acesso já foi respondida.");
		}

		return accessRequest;
	}

	private User loadAdminByEmail(String email) {
		User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Administrador não encontrado."));

		boolean isAdmin = user.getRoles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getCode()));
		if (!isAdmin || user.getCompanyName() == null || user.getCompanyName().isBlank()) {
			throw new IllegalArgumentException("Somente administradores de empresa podem responder essas solicitações.");
		}

		return user;
	}

	private String resolvePrimaryRole(User user) {
		if (user.getRoles().stream().anyMatch(role -> "EMPLOYEE".equalsIgnoreCase(role.getCode()))) {
			return "employee";
		}
		if (user.getRoles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.getCode()))) {
			return "admin";
		}
		return "user";
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe o email do administrador.");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
