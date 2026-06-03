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
import com.helpdesk.helpdesk.domain.CompanyAccessRequestType;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.TeamMembershipNotification;
import com.helpdesk.helpdesk.domain.TeamMembershipNotificationType;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.auth.RegisterInviteResponse;
import com.helpdesk.helpdesk.dto.company.CompanyAdminInviteResponse;
import com.helpdesk.helpdesk.dto.company.CreateCompanyAdminInviteRequest;
import com.helpdesk.helpdesk.dto.company.RespondCompanyAccessRequest;
import com.helpdesk.helpdesk.dto.notification.CompanyAccessRequestNotificationResponse;
import com.helpdesk.helpdesk.dto.notification.CompanyAdminInviteNotificationResponse;
import com.helpdesk.helpdesk.repository.CompanyAccessRequestRepository;
import com.helpdesk.helpdesk.repository.CompanyMembershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.TeamMembershipNotificationRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.util.BrazilianDocumentValidator;

@Service
public class CompanyAccessRequestService {

	private final CompanyAccessRequestRepository companyAccessRequestRepository;
	private final CompanyMembershipRepository companyMembershipRepository;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final TeamMembershipNotificationRepository teamMembershipNotificationRepository;
	private final CompanyInvitationEmailService companyInvitationEmailService;
	private final EmailDomainValidationService emailDomainValidationService;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;

	public CompanyAccessRequestService(
		CompanyAccessRequestRepository companyAccessRequestRepository,
		CompanyMembershipRepository companyMembershipRepository,
		UserRepository userRepository,
		RoleRepository roleRepository,
		TeamMembershipNotificationRepository teamMembershipNotificationRepository,
		CompanyInvitationEmailService companyInvitationEmailService,
		EmailDomainValidationService emailDomainValidationService,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService
	) {
		this.companyAccessRequestRepository = companyAccessRequestRepository;
		this.companyMembershipRepository = companyMembershipRepository;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.teamMembershipNotificationRepository = teamMembershipNotificationRepository;
		this.companyInvitationEmailService = companyInvitationEmailService;
		this.emailDomainValidationService = emailDomainValidationService;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
	}

	@Transactional
	public void createPendingRequest(User requesterUser, User targetCompany) {
		if (companyAccessRequestRepository.existsByRequesterUserIdAndRequestTypeAndStatus(
			requesterUser.getId(),
			CompanyAccessRequestType.USER_REQUEST,
			CompanyAccessRequestStatus.PENDING
		)) {
			throw new IllegalArgumentException("Já existe uma solicitação pendente aguardando aprovação do administrador.");
		}

		CompanyAccessRequest request = new CompanyAccessRequest();
		request.setRequesterUser(requesterUser);
		applyRequesterSnapshot(
			request,
			requesterUser.getFullName(),
			requesterUser.getEmail(),
			requesterUser.getDocumentNumber()
		);
		request.setTargetCompany(targetCompany);
		request.setRequestType(CompanyAccessRequestType.USER_REQUEST);
		request.setStatus(CompanyAccessRequestStatus.PENDING);
		companyAccessRequestRepository.save(request);
	}

	@Transactional
	public CompanyAdminInviteResponse createAdminInvite(CreateCompanyAdminInviteRequest request) {
		User admin = loadAdminByEmail(request.invitedByEmail());
		String normalizedEmail = normalizeEmail(request.email());
		emailDomainValidationService.ensurePublicEmailDomainExists(normalizedEmail);
		String normalizedDocumentNumber = normalizeDocumentNumber(request.documentNumber());

		if (!BrazilianDocumentValidator.isValidCpf(normalizedDocumentNumber)) {
			throw new IllegalArgumentException("Informe um CPF válido para enviar o convite.");
		}
		if (admin.getEmail().equalsIgnoreCase(normalizedEmail)
			|| normalizedDocumentNumber.equals(normalizeDocumentNumber(admin.getDocumentNumber()))) {
			throw new IllegalArgumentException("Você não pode convidar a si mesmo para entrar na própria empresa.");
		}

		User invitedUser = findInvitedUser(normalizedEmail, normalizedDocumentNumber);
		String invitedName = blankToNull(request.fullName());

		if (invitedUser != null) {
			ensureInvitableUser(invitedUser);
			invitedName = invitedUser.getFullName();
			normalizedEmail = normalizeEmail(invitedUser.getEmail());
			normalizedDocumentNumber = normalizeDocumentNumber(invitedUser.getDocumentNumber());
		}

		if (companyAccessRequestRepository.existsByTargetCompanyIdAndRequesterEmailIgnoreCaseAndRequestTypeAndStatus(
			admin.getId(),
			normalizedEmail,
			CompanyAccessRequestType.ADMIN_INVITE,
			CompanyAccessRequestStatus.PENDING
		)) {
			throw new IllegalArgumentException("Já existe um convite pendente dessa empresa para esse email.");
		}

		CompanyAccessRequest accessRequest = new CompanyAccessRequest();
		accessRequest.setRequesterUser(invitedUser);
		applyRequesterSnapshot(accessRequest, invitedName, normalizedEmail, normalizedDocumentNumber);
		accessRequest.setTargetCompany(admin);
		accessRequest.setRequestType(CompanyAccessRequestType.ADMIN_INVITE);
		accessRequest.setStatus(CompanyAccessRequestStatus.PENDING);
		accessRequest.setExpiresAt(OffsetDateTime.now().plusDays(7));

		String deliveryChannel = "PLATFORM";
		if (invitedUser == null) {
			String inviteToken = UUID.randomUUID().toString();
			accessRequest.setInviteTokenHash(inviteToken);
			companyInvitationEmailService.sendInvitation(
				normalizedEmail,
				invitedName == null ? "convidado(a)" : invitedName,
				resolveCompanyName(admin),
				inviteToken
			);
			deliveryChannel = "EMAIL";
		}

		CompanyAccessRequest savedRequest = companyAccessRequestRepository.save(accessRequest);
		return new CompanyAdminInviteResponse(
			savedRequest.getId(),
			savedRequest.getRequesterName(),
			savedRequest.getRequesterEmail(),
			savedRequest.getRequesterDocumentNumber(),
			resolveCompanyName(admin),
			admin.getCompanyType() == null ? null : admin.getCompanyType().name(),
			deliveryChannel,
			savedRequest.getExpiresAt()
		);
	}

	@Transactional(readOnly = true)
	public List<CompanyAccessRequestNotificationResponse> listPendingNotifications(String email) {
		User admin = loadAdminByEmail(email);
		return companyAccessRequestRepository.findByTargetCompanyIdAndRequestTypeAndStatusOrderByCreatedAtDesc(
			admin.getId(),
			CompanyAccessRequestType.USER_REQUEST,
			CompanyAccessRequestStatus.PENDING
		)
			.stream()
			.map(this::toNotificationResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<CompanyAdminInviteNotificationResponse> listPendingAdminInvites(String email) {
		User user = loadUserByEmail(email);
		return companyAccessRequestRepository.findByRequesterUserIdAndRequestTypeAndStatusOrderByCreatedAtDesc(
			user.getId(),
			CompanyAccessRequestType.ADMIN_INVITE,
			CompanyAccessRequestStatus.PENDING
		)
			.stream()
			.filter(this::isNotExpired)
			.map(this::toAdminInviteNotificationResponse)
			.toList();
	}

	@Transactional
	public void accept(UUID requestId, RespondCompanyAccessRequest request) {
		User admin = loadAdminByEmail(request.email());
		CompanyAccessRequest accessRequest = loadPendingRequestForAdminResponse(requestId, admin);
		User requester = accessRequest.getRequesterUser();
		User membershipCompanyOwner = resolveMembershipCompanyOwner(accessRequest, admin);

		ensureCompanyMembership(requester, membershipCompanyOwner);
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
		CompanyAccessRequest accessRequest = loadPendingRequestForAdminResponse(requestId, admin);
		User requester = accessRequest.getRequesterUser();

		if (requester.getStatus() == UserStatus.PENDING) {
			requester.setStatus(UserStatus.ACTIVE);
		}
		userRepository.save(requester);

		accessRequest.setStatus(CompanyAccessRequestStatus.DECLINED);
		accessRequest.setRespondedBy(admin);
		accessRequest.setRespondedAt(OffsetDateTime.now());
		companyAccessRequestRepository.save(accessRequest);
	}

	@Transactional
	public void acceptAdminInvite(UUID requestId, RespondCompanyAccessRequest request) {
		User invitedUser = loadUserByEmail(request.email());
		CompanyAccessRequest accessRequest = loadPendingAdminInviteForUser(requestId, invitedUser);

		attachUserToCompany(invitedUser, accessRequest.getTargetCompany());
		applyRequesterSnapshot(
			accessRequest,
			invitedUser.getFullName(),
			invitedUser.getEmail(),
			invitedUser.getDocumentNumber()
		);
		accessRequest.setRequesterUser(invitedUser);
		accessRequest.setStatus(CompanyAccessRequestStatus.APPROVED);
		accessRequest.setRespondedBy(invitedUser);
		accessRequest.setRespondedAt(OffsetDateTime.now());
		companyAccessRequestRepository.save(accessRequest);
		createCompanyJoinedNotification(invitedUser, accessRequest.getTargetCompany());
	}

	@Transactional
	public void declineAdminInvite(UUID requestId, RespondCompanyAccessRequest request) {
		User invitedUser = loadUserByEmail(request.email());
		CompanyAccessRequest accessRequest = loadPendingAdminInviteForUser(requestId, invitedUser);

		applyRequesterSnapshot(
			accessRequest,
			invitedUser.getFullName(),
			invitedUser.getEmail(),
			invitedUser.getDocumentNumber()
		);
		accessRequest.setRequesterUser(invitedUser);
		accessRequest.setStatus(CompanyAccessRequestStatus.DECLINED);
		accessRequest.setRespondedBy(invitedUser);
		accessRequest.setRespondedAt(OffsetDateTime.now());
		companyAccessRequestRepository.save(accessRequest);
	}

	@Transactional(readOnly = true)
	public RegisterInviteResponse getRegisterInvite(String inviteToken) {
		CompanyAccessRequest invite = loadPendingInviteByToken(inviteToken);
		User targetCompany = invite.getTargetCompany();
		String participation = targetCompany.getCompanyType() == CompanyType.RESPONDER ? "responder" : "requester";
		String role = targetCompany.getCompanyType() == CompanyType.RESPONDER ? "employee" : "user";

		return new RegisterInviteResponse(
			invite.getRequesterName(),
			invite.getRequesterEmail(),
			invite.getRequesterDocumentNumber(),
			resolveCompanyName(targetCompany),
			targetCompany.getCompanyType() == null ? null : targetCompany.getCompanyType().name(),
			participation,
			role
		);
	}

	@Transactional
	public void acceptAdminInviteDuringRegistration(User user, String inviteToken) {
		CompanyAccessRequest invite = loadPendingInviteByToken(inviteToken);
		attachUserToCompany(user, invite.getTargetCompany());
		applyRequesterSnapshot(invite, user.getFullName(), user.getEmail(), user.getDocumentNumber());
		invite.setRequesterUser(user);
		invite.setStatus(CompanyAccessRequestStatus.APPROVED);
		invite.setRespondedBy(user);
		invite.setRespondedAt(OffsetDateTime.now());
		companyAccessRequestRepository.save(invite);
		createCompanyJoinedNotification(user, invite.getTargetCompany());
	}

	@Transactional
	public void attachApprovedUserToCompany(User user, User companyOwner) {
		attachUserToCompany(user, companyOwner);
	}

	@Transactional(readOnly = true)
	public void ensureInviteMatchesCurrentTenant(String inviteToken) {
		loadPendingInviteByToken(inviteToken);
	}

	@Transactional
	public void attachPendingAdminInvitesToRegisteredUser(User user) {
		List<CompanyAccessRequest> pendingInvites = companyAccessRequestRepository.findPendingAdminInvitesForIdentity(
			CompanyAccessRequestType.ADMIN_INVITE,
			CompanyAccessRequestStatus.PENDING,
			user.getEmail(),
			normalizeDocumentNumber(user.getDocumentNumber())
		);

		List<CompanyAccessRequest> invitesToAttach = pendingInvites.stream()
			.filter(this::isNotExpired)
			.filter(invite -> invite.getRequesterUser() == null)
			.toList();

		if (invitesToAttach.isEmpty()) {
			return;
		}

		for (CompanyAccessRequest invite : invitesToAttach) {
			invite.setRequesterUser(user);
			applyRequesterSnapshot(invite, user.getFullName(), user.getEmail(), user.getDocumentNumber());
		}

		companyAccessRequestRepository.saveAll(invitesToAttach);
	}

	private CompanyAccessRequestNotificationResponse toNotificationResponse(CompanyAccessRequest request) {
		User requesterUser = request.getRequesterUser();
		return new CompanyAccessRequestNotificationResponse(
			request.getId(),
			requesterUser == null ? null : requesterUser.getId(),
			request.getRequesterName(),
			request.getRequesterEmail(),
			request.getRequesterDocumentNumber(),
			requesterUser == null ? resolveRequestedRole(request) : resolveRequestedRole(request),
			resolveRequestedCompanyName(request),
			resolveRequestedCompanyType(request),
			request.getStatus().name(),
			request.getCreatedAt()
		);
	}

	private CompanyAdminInviteNotificationResponse toAdminInviteNotificationResponse(CompanyAccessRequest request) {
		return new CompanyAdminInviteNotificationResponse(
			request.getId(),
			request.getRequesterName(),
			request.getRequesterEmail(),
			request.getRequesterDocumentNumber(),
			resolveRequestedRole(request.getTargetCompany()),
			resolveCompanyName(request.getTargetCompany()),
			request.getTargetCompany().getCompanyType() == null ? null : request.getTargetCompany().getCompanyType().name(),
			request.getStatus().name(),
			request.getCreatedAt()
		);
	}

	private CompanyAccessRequest loadPendingRequestForAdminResponse(UUID requestId, User admin) {
		CompanyAccessRequest accessRequest = companyAccessRequestRepository.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Solicitação de acesso não encontrada."));

		if (accessRequest.getRequestType() != CompanyAccessRequestType.USER_REQUEST) {
			throw new IllegalArgumentException("Essa solicitação não pode ser respondida por esse fluxo.");
		}
		if (!accessRequest.getTargetCompany().getId().equals(admin.getId())) {
			throw new IllegalArgumentException("Somente o administrador da empresa selecionada pode responder a solicitação.");
		}
		if (accessRequest.getStatus() != CompanyAccessRequestStatus.PENDING) {
			throw new IllegalArgumentException("Essa solicitação de acesso já foi respondida.");
		}

		return accessRequest;
	}

	private CompanyAccessRequest loadPendingAdminInviteForUser(UUID requestId, User user) {
		CompanyAccessRequest accessRequest = companyAccessRequestRepository.findById(requestId)
			.orElseThrow(() -> new NotFoundException("Convite para a empresa não encontrado."));

		if (accessRequest.getRequestType() != CompanyAccessRequestType.ADMIN_INVITE) {
			throw new IllegalArgumentException("Esse convite não pode ser respondido por esse fluxo.");
		}
		if (accessRequest.getStatus() != CompanyAccessRequestStatus.PENDING) {
			throw new IllegalArgumentException("Esse convite para empresa já foi respondido.");
		}
		if (!isNotExpired(accessRequest)) {
			throw new IllegalArgumentException("Esse convite expirou e não pode mais ser respondido.");
		}

		User requesterUser = accessRequest.getRequesterUser();
		if (requesterUser != null && !requesterUser.getId().equals(user.getId())) {
			throw new IllegalArgumentException("Esse convite não pertence ao usuário informado.");
		}

		String normalizedUserEmail = normalizeEmail(user.getEmail());
		String normalizedUserDocument = normalizeDocumentNumber(user.getDocumentNumber());
		if (!normalizedUserEmail.equals(normalizeEmail(accessRequest.getRequesterEmail()))
			&& !normalizedUserDocument.equals(normalizeDocumentNumber(accessRequest.getRequesterDocumentNumber()))) {
			throw new IllegalArgumentException("Esse convite não pertence ao usuário informado.");
		}

		ensureInvitableUser(user);
		return accessRequest;
	}

	private CompanyAccessRequest loadPendingInviteByToken(String inviteToken) {
		if (inviteToken == null || inviteToken.isBlank()) {
			throw new IllegalArgumentException("O convite informado é inválido.");
		}

		CompanyAccessRequest invite = companyAccessRequestRepository.findByInviteTokenHashAndRequestTypeAndStatus(
			inviteToken.trim(),
			CompanyAccessRequestType.ADMIN_INVITE,
			CompanyAccessRequestStatus.PENDING
		).orElseThrow(() -> new NotFoundException("Convite não encontrado ou já utilizado."));

		if (!isNotExpired(invite)) {
			throw new IllegalArgumentException("Esse convite expirou e não pode mais ser utilizado.");
		}
		if (tenantAccessService.hasCurrentTenant()
			&& !invite.getTargetCompany().getId().equals(tenantAccessService.requireCurrentTenantOwnerUserId())) {
			throw new IllegalArgumentException("Esse convite não pertence ao tenant atual.");
		}

		return invite;
	}

	private void attachUserToCompany(User user, User companyOwner) {
		ensureInvitableUser(user);
		ensureCompanyMembership(user, companyOwner);
		user.setStatus(UserStatus.ACTIVE);
		user.getRoles().add(loadRoleForCompanyType(companyOwner.getCompanyType()));
		userRepository.save(user);
	}

	private void createCompanyJoinedNotification(User recipient, User companyOwner) {
		TeamMembershipNotification notification = new TeamMembershipNotification();
		notification.setRecipient(recipient);
		notification.setRemovedBy(companyOwner);
		notification.setCompanyName(resolveCompanyName(companyOwner));
		notification.setType(TeamMembershipNotificationType.COMPANY_JOINED);
		teamMembershipNotificationRepository.save(notification);
	}

	private Role loadRoleForCompanyType(CompanyType companyType) {
		String roleCode = companyType == CompanyType.RESPONDER ? "EMPLOYEE" : "USER";
		return roleRepository.findByCode(roleCode)
			.orElseThrow(() -> new NotFoundException("Perfil de acesso não encontrado para o convite."));
	}

	private User findInvitedUser(String normalizedEmail, String normalizedDocumentNumber) {
		User userByEmail = scopedUserLookupService.findUniqueByEmailForRegistrationScope(normalizedEmail).orElse(null);
		List<User> usersByDocument = tenantAccessService.hasCurrentTenant()
			? scopedUserLookupService.findAllByDocumentInCurrentTenant(normalizedDocumentNumber)
			: scopedUserLookupService.findStandaloneUsersByDocument(normalizedDocumentNumber);
		User userByDocument = usersByDocument
			.stream()
			.filter(user -> !hasRole(user, "ADMIN"))
			.findFirst()
			.orElse(usersByDocument.stream().findFirst().orElse(null));

		if (userByEmail != null && userByDocument != null && !userByEmail.getId().equals(userByDocument.getId())) {
			throw new IllegalArgumentException("O email e o CPF informados pertencem a cadastros diferentes.");
		}

		return userByDocument != null ? userByDocument : userByEmail;
	}

	private void ensureInvitableUser(User invitedUser) {
		if (hasRole(invitedUser, "ADMIN")) {
			throw new IllegalArgumentException("Administradores não podem ser convidados para entrar na empresa como funcionários.");
		}
	}

	private void ensureCompanyMembership(User user, User companyOwner) {
		if (companyMembershipRepository.existsByUserIdAndCompanyOwnerId(user.getId(), companyOwner.getId())) {
			if (user.getCompanyOwner() == null) {
				user.setCompanyOwner(companyOwner);
			}
			return;
		}

		com.helpdesk.helpdesk.domain.CompanyMembership membership = new com.helpdesk.helpdesk.domain.CompanyMembership();
		membership.setUser(user);
		membership.setCompanyOwner(companyOwner);
		companyMembershipRepository.save(membership);

		if (user.getCompanyOwner() == null) {
			user.setCompanyOwner(companyOwner);
		}
	}

	private void applyRequesterSnapshot(
		CompanyAccessRequest request,
		String requesterName,
		String requesterEmail,
		String requesterDocumentNumber
	) {
		request.setRequesterName(blankToNull(requesterName));
		request.setRequesterEmail(normalizeEmail(requesterEmail));
		request.setRequesterDocumentNumber(normalizeDocumentNumber(requesterDocumentNumber));
	}

	private User loadAdminByEmail(String email) {
		User user = loadUserByEmail(email);

		boolean isAdmin = hasRole(user, "ADMIN");
		if (!isAdmin || user.getCompanyName() == null || user.getCompanyName().isBlank()) {
			throw new IllegalArgumentException("Somente administradores de empresa podem responder essas solicitações.");
		}

		return user;
	}

	private User loadUserByEmail(String email) {
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(email))
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
		tenantAccessService.ensureUserBelongsToCurrentTenant(user, "Esse usuário não pertence ao tenant atual.");
		return user;
	}

	private boolean isNotExpired(CompanyAccessRequest request) {
		return request.getExpiresAt() == null || !request.getExpiresAt().isBefore(OffsetDateTime.now());
	}

	private String resolveRequestedRole(User targetCompany) {
		return targetCompany.getCompanyType() == CompanyType.RESPONDER ? "employee" : "user";
	}

	private String resolveRequestedRole(CompanyAccessRequest request) {
		User requesterUser = request.getRequesterUser();
		if (requesterUser != null
			&& requesterUser.getCompanyOwner() != null
			&& requesterUser.getCompanyOwner().getCompanyType() == CompanyType.REQUESTER
			&& request.getTargetCompany().getCompanyType() == CompanyType.RESPONDER) {
			return "user";
		}

		return resolveRequestedRole(request.getTargetCompany());
	}

	private String resolveRequestedCompanyName(CompanyAccessRequest request) {
		User requesterUser = request.getRequesterUser();
		if (requesterUser != null
			&& requesterUser.getCompanyOwner() != null
			&& requesterUser.getCompanyOwner().getCompanyType() == CompanyType.REQUESTER
			&& request.getTargetCompany().getCompanyType() == CompanyType.RESPONDER) {
			return resolveCompanyName(requesterUser.getCompanyOwner());
		}

		return resolveCompanyName(request.getTargetCompany());
	}

	private String resolveRequestedCompanyType(CompanyAccessRequest request) {
		User requesterUser = request.getRequesterUser();
		if (requesterUser != null
			&& requesterUser.getCompanyOwner() != null
			&& requesterUser.getCompanyOwner().getCompanyType() == CompanyType.REQUESTER
			&& request.getTargetCompany().getCompanyType() == CompanyType.RESPONDER) {
			return CompanyType.REQUESTER.name();
		}

		return request.getTargetCompany().getCompanyType() == null ? null : request.getTargetCompany().getCompanyType().name();
	}

	private User resolveMembershipCompanyOwner(CompanyAccessRequest request, User admin) {
		User requester = request.getRequesterUser();
		if (requester != null
			&& requester.getCompanyOwner() != null
			&& requester.getCompanyOwner().getCompanyType() == CompanyType.REQUESTER
			&& admin.getCompanyType() == CompanyType.RESPONDER) {
			return requester.getCompanyOwner();
		}

		return admin;
	}

	private String resolveCompanyName(User companyOwner) {
		String companyName = blankToNull(companyOwner.getCompanyName());
		return companyName == null ? companyOwner.getFullName() : companyName;
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream().anyMatch(role -> roleCode.equalsIgnoreCase(role.getCode()));
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String normalizeDocumentNumber(String value) {
		String trimmedValue = blankToNull(value);
		if (trimmedValue == null) {
			return "";
		}
		return trimmedValue.replaceAll("\\D", "");
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe um email válido.");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
