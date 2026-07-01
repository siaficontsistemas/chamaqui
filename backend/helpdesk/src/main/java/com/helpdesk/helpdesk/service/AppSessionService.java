package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.common.UnauthorizedException;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.helpdesk.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class AppSessionService {

	public static final String AUTHENTICATED_REQUEST_USER_ATTRIBUTE = "helpdesk.authenticatedAppUser";
	private static final String APP_SESSION_KEY = "helpdesk.appUserId";
	private static final int SESSION_TTL_SECONDS = 60 * 60 * 12;

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final TenantAccessService tenantAccessService;
	private final AuditTrailService auditTrailService;
	private final AppAuthTokenService appAuthTokenService;

	public AppSessionService(
		UserRepository userRepository,
		UserMapper userMapper,
		TenantAccessService tenantAccessService,
		AuditTrailService auditTrailService,
		AppAuthTokenService appAuthTokenService
	) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
		this.tenantAccessService = tenantAccessService;
		this.auditTrailService = auditTrailService;
		this.appAuthTokenService = appAuthTokenService;
	}

	@Transactional
	public AuthResponse login(User user, HttpSession session) {
		User persistedUser = requireActiveUser(user.getId());
		persistedUser.setLastLoginAt(OffsetDateTime.now());
		session.setAttribute(APP_SESSION_KEY, persistedUser.getId().toString());
		session.setMaxInactiveInterval(SESSION_TTL_SECONDS);
		auditTrailService.recordUserAction("APP_LOGIN", persistedUser, "user-session", persistedUser.getId());
		return userMapper.toAuthResponse(persistedUser, appAuthTokenService.issueToken(persistedUser));
	}

	@Transactional(readOnly = true)
	public AuthResponse me(HttpSession session) {
		User user = requireUser(session);
		return userMapper.toAuthResponse(user, appAuthTokenService.issueToken(user));
	}

	@Transactional(readOnly = true)
	public User requireUser(HttpSession session) {
		User requestUser = resolveAuthenticatedRequestUser();
		if (requestUser != null) {
			return validateCurrentTenantUser(requestUser);
		}

		Object rawUserId = session.getAttribute(APP_SESSION_KEY);
		if (!(rawUserId instanceof String rawValue) || rawValue.isBlank()) {
			throw new UnauthorizedException("Faça login para continuar.");
		}

		User user = userRepository.findById(UUID.fromString(rawValue))
			.orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));

		return validateCurrentTenantUser(user);
	}

	public boolean hasAuthenticatedUser(HttpSession session) {
		try {
			requireUser(session);
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	public String requireCurrentEmail(HttpSession session) {
		return normalizeEmail(requireUser(session).getEmail());
	}

	public void logout(HttpSession session) {
		User currentUser = null;
		try {
			currentUser = requireUser(session);
		} catch (RuntimeException ignored) {
			// Ignora quando a sessao já não está válida.
		}
		session.invalidate();
		if (currentUser != null) {
			auditTrailService.recordUserAction("APP_LOGOUT", currentUser, "user-session", currentUser.getId());
		}
	}

	private User requireActiveUser(UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
		return validateCurrentTenantUser(user);
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return "";
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private User resolveAuthenticatedRequestUser() {
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
			return null;
		}

		Object requestUser = attributes.getRequest().getAttribute(AUTHENTICATED_REQUEST_USER_ATTRIBUTE);
		return requestUser instanceof User user ? user : null;
	}

	private User validateCurrentTenantUser(User user) {
		if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
			throw new UnauthorizedException("A sessão atual não é mais válida.");
		}
		if (!tenantAccessService.belongsToCurrentTenant(user)) {
			throw new UnauthorizedException("A sessão atual não pertence a este ambiente.");
		}
		return user;
	}
}
