package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Locale;
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
	private static final int SESSION_TTL_SECONDS = 60 * 60 * 12;

	private final UserRepository userRepository;
	private final UserMapper userMapper;
	private final TenantAccessService tenantAccessService;

	public AppSessionService(
		UserRepository userRepository,
		UserMapper userMapper,
		TenantAccessService tenantAccessService
	) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
		this.tenantAccessService = tenantAccessService;
	}

	@Transactional
	public AuthResponse login(User user, HttpSession session) {
		User persistedUser = requireActiveUser(user.getId());
		persistedUser.setLastLoginAt(OffsetDateTime.now());
		session.setAttribute(APP_SESSION_KEY, persistedUser.getId().toString());
		session.setMaxInactiveInterval(SESSION_TTL_SECONDS);
		return userMapper.toAuthResponse(persistedUser);
	}

	@Transactional(readOnly = true)
	public AuthResponse me(HttpSession session) {
		return userMapper.toAuthResponse(requireUser(session));
	}

	@Transactional(readOnly = true)
	public User requireUser(HttpSession session) {
		Object rawUserId = session.getAttribute(APP_SESSION_KEY);
		if (!(rawUserId instanceof String rawValue) || rawValue.isBlank()) {
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
		session.invalidate();
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
