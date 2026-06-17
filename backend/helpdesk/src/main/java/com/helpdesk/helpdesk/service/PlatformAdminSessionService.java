package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.common.UnauthorizedException;
import com.helpdesk.helpdesk.domain.PlatformAdminUser;
import com.helpdesk.helpdesk.dto.platformadmin.PlatformAdminAuthResponse;
import com.helpdesk.helpdesk.dto.platformadmin.PlatformAdminLoginRequest;
import com.helpdesk.helpdesk.repository.PlatformAdminUserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class PlatformAdminSessionService {

	private static final String PLATFORM_ADMIN_SESSION_KEY = "helpdesk.platformAdminUserId";

	private final PlatformAdminUserRepository platformAdminUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final String allowedEmail;
	private final AuditTrailService auditTrailService;

	public PlatformAdminSessionService(
		PlatformAdminUserRepository platformAdminUserRepository,
		PasswordEncoder passwordEncoder,
		@Value("${app.platform-admin.email:}") String allowedEmail,
		AuditTrailService auditTrailService
	) {
		this.platformAdminUserRepository = platformAdminUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.allowedEmail = allowedEmail == null ? "" : allowedEmail.trim().toLowerCase(Locale.ROOT);
		this.auditTrailService = auditTrailService;
	}

	@Transactional
	public PlatformAdminAuthResponse login(PlatformAdminLoginRequest request, HttpSession session) {
		String normalizedEmail = normalizeEmail(request.email());
		if (!allowedEmail.isBlank() && !allowedEmail.equals(normalizedEmail)) {
			throw new UnauthorizedException("Somente o login autorizado da plataforma pode acessar este painel.");
		}
		PlatformAdminUser user = platformAdminUserRepository.findByEmailIgnoreCase(normalizedEmail)
			.orElseThrow(() -> new UnauthorizedException("Email ou senha inválidos para o administrador da plataforma."));

		if (!user.isActive()) {
			throw new UnauthorizedException("O administrador da plataforma está inativo.");
		}
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new UnauthorizedException("Email ou senha inválidos para o administrador da plataforma.");
		}

		user.setLastLoginAt(OffsetDateTime.now());
		session.setAttribute(PLATFORM_ADMIN_SESSION_KEY, user.getId().toString());
		session.setMaxInactiveInterval(60 * 60 * 12);
		auditTrailService.recordPlatformAdminAction("PLATFORM_ADMIN_LOGIN", user, "platform-admin-session", user.getId());
		return toResponse(user);
	}

	@Transactional(readOnly = true)
	public PlatformAdminAuthResponse me(HttpSession session) {
		return toResponse(requireUser(session));
	}

	@Transactional(readOnly = true)
	public PlatformAdminUser requireUser(HttpSession session) {
		Object rawUserId = session.getAttribute(PLATFORM_ADMIN_SESSION_KEY);
		if (!(rawUserId instanceof String rawValue) || rawValue.isBlank()) {
			throw new UnauthorizedException("Faça login como administrador da plataforma para continuar.");
		}

		PlatformAdminUser user = platformAdminUserRepository.findById(java.util.UUID.fromString(rawValue))
			.orElseThrow(() -> new NotFoundException("Administrador da plataforma não encontrado."));
		if (!user.isActive()) {
			throw new UnauthorizedException("O administrador da plataforma está inativo.");
		}
		return user;
	}

	public void logout(HttpSession session) {
		session.invalidate();
	}

	private PlatformAdminAuthResponse toResponse(PlatformAdminUser user) {
		return new PlatformAdminAuthResponse(user.getId(), user.getFullName(), user.getEmail());
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return "";
		}
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
