package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.common.UnauthorizedException;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.helpdesk.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class AppSessionService {

	private static final String APP_SESSION_KEY = "helpdesk.appUserId";
	private static final String APP_SESSION_BY_SCOPE_KEY = "helpdesk.appUserIdByScope";
	private static final String MAIN_HOST_SCOPE = "__main_host__";
	private static final int SESSION_TTL_SECONDS = 60 * 60 * 12;

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final TenantAccessService tenantAccessService;
	private final AuditTrailService auditTrailService;

	public AppSessionService(
		UserRepository userRepository,
		UserMapper userMapper,
		TenantAccessService tenantAccessService,
		AuditTrailService auditTrailService
	) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
		this.tenantAccessService = tenantAccessService;
		this.auditTrailService = auditTrailService;
	}

	@Transactional
	public AuthResponse login(User user, HttpSession session) {
		User persistedUser = requireActiveUser(user.getId());
		persistedUser.setLastLoginAt(OffsetDateTime.now());
		Map<String, String> sessionsByScope = getSessionsByScope(session);
		sessionsByScope.put(resolveCurrentScopeKey(), persistedUser.getId().toString());
		session.setAttribute(APP_SESSION_BY_SCOPE_KEY, sessionsByScope);
		session.removeAttribute(APP_SESSION_KEY);
		session.setMaxInactiveInterval(SESSION_TTL_SECONDS);
		auditTrailService.recordUserAction("APP_LOGIN", persistedUser, "user-session", persistedUser.getId());
		return userMapper.toAuthResponse(persistedUser);
	}

	@Transactional(readOnly = true)
	public AuthResponse me(HttpSession session) {
		return userMapper.toAuthResponse(requireUser(session));
	}

	@Transactional(readOnly = true)
	public User requireUser(HttpSession session) {
		String rawValue = resolveCurrentSessionUserId(session)
			.orElseThrow(() -> new UnauthorizedException("Faça login para continuar."));
		if (rawValue.isBlank()) {
			throw new UnauthorizedException("Faça login para continuar.");
		}

		User user = userRepository.findById(UUID.fromString(rawValue))
			.orElseThrow(() -> new NotFoundException("Usuário autenticado não encontrado."));

		if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
			throw new UnauthorizedException("A sessão atual não é mais válida.");
		}
		if (!tenantAccessService.belongsToCurrentTenant(user)) {
			throw new UnauthorizedException("A sessão atual não pertence a este ambiente.");
		}

		return user;
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
		Map<String, String> sessionsByScope = getSessionsByScope(session);
		sessionsByScope.remove(resolveCurrentScopeKey());
		session.removeAttribute(APP_SESSION_KEY);
		if (sessionsByScope.isEmpty()) {
			session.invalidate();
		} else {
			session.setAttribute(APP_SESSION_BY_SCOPE_KEY, sessionsByScope);
		}
		if (currentUser != null) {
			auditTrailService.recordUserAction("APP_LOGOUT", currentUser, "user-session", currentUser.getId());
		}
	}

	private Optional<String> resolveCurrentSessionUserId(HttpSession session) {
		Map<String, String> sessionsByScope = getSessionsByScope(session);
		String scopedUserId = sessionsByScope.get(resolveCurrentScopeKey());
		if (scopedUserId != null && !scopedUserId.isBlank()) {
			return Optional.of(scopedUserId);
		}

		Object legacyRawUserId = session.getAttribute(APP_SESSION_KEY);
		if (legacyRawUserId instanceof String legacyUserId && !legacyUserId.isBlank()) {
			sessionsByScope.put(resolveCurrentScopeKey(), legacyUserId);
			session.setAttribute(APP_SESSION_BY_SCOPE_KEY, sessionsByScope);
			session.removeAttribute(APP_SESSION_KEY);
			return Optional.of(legacyUserId);
		}

		return Optional.empty();
	}

	private Map<String, String> getSessionsByScope(HttpSession session) {
		Object rawValue = session.getAttribute(APP_SESSION_BY_SCOPE_KEY);
		if (rawValue instanceof Map<?, ?> rawMap) {
			Map<String, String> sessionsByScope = new HashMap<>();
			rawMap.forEach((key, value) -> {
				if (key instanceof String scopeKey && value instanceof String userId && !scopeKey.isBlank() && !userId.isBlank()) {
					sessionsByScope.put(scopeKey, userId);
				}
			});
			return sessionsByScope;
		}
		return new HashMap<>();
	}

	private String resolveCurrentScopeKey() {
		return tenantAccessService.getCurrentTenantOwnerUserId()
			.map(UUID::toString)
			.orElse(MAIN_HOST_SCOPE);
	}

	private User requireActiveUser(UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
		if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
			throw new UnauthorizedException("O usuário não está ativo para acessar o sistema.");
		}
		if (!tenantAccessService.belongsToCurrentTenant(user)) {
			throw new UnauthorizedException("O usuário não pertence a este ambiente.");
		}
		return user;
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return "";
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
