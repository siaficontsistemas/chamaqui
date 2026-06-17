package com.helpdesk.helpdesk.service;

import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.auth.ForgotPasswordRequest;
import com.helpdesk.helpdesk.dto.auth.ResetPasswordRequest;
import com.helpdesk.helpdesk.dto.common.OperationMessageResponse;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class PasswordRecoveryService {

	private static final int TOKEN_EXPIRATION_MINUTES = 30;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordRecoveryEmailService passwordRecoveryEmailService;
	private final TenantAccessService tenantAccessService;
	private final ScopedUserLookupService scopedUserLookupService;
	private final SensitiveTokenService sensitiveTokenService;
	private final AuditTrailService auditTrailService;

	public PasswordRecoveryService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		PasswordRecoveryEmailService passwordRecoveryEmailService,
		TenantAccessService tenantAccessService,
		ScopedUserLookupService scopedUserLookupService,
		SensitiveTokenService sensitiveTokenService,
		AuditTrailService auditTrailService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordRecoveryEmailService = passwordRecoveryEmailService;
		this.tenantAccessService = tenantAccessService;
		this.scopedUserLookupService = scopedUserLookupService;
		this.sensitiveTokenService = sensitiveTokenService;
		this.auditTrailService = auditTrailService;
	}

	@Transactional
	public OperationMessageResponse requestReset(ForgotPasswordRequest request) {
		String normalizedEmail = normalizeEmail(request.email());
		User user = tenantAccessService.hasCurrentTenant()
			? scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizedEmail).orElse(null)
			: scopedUserLookupService.resolveLoginCandidate(normalizedEmail).orElse(null);

		if (user == null) {
			auditTrailService.recordAnonymousAction("PASSWORD_RESET_REQUESTED_UNKNOWN_EMAIL", normalizedEmail, "password-reset", null);
			return new OperationMessageResponse(
				"Se o email estiver cadastrado, enviaremos um link para redefinir a senha."
			);
		}

		String rawToken = sensitiveTokenService.generateUrlSafeToken();
		user.setPasswordResetTokenHash(hashToken(rawToken));
		user.setPasswordResetTokenExpiresAt(OffsetDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES));
		userRepository.save(user);

		passwordRecoveryEmailService.sendResetPasswordEmail(user.getEmail(), user.getFullName(), rawToken);
		auditTrailService.recordAnonymousAction("PASSWORD_RESET_REQUESTED", user.getEmail(), "user-profile", user.getId());

		return new OperationMessageResponse(
			"Se o email estiver cadastrado, enviaremos um link para redefinir a senha."
		);
	}

	@Transactional
	public OperationMessageResponse resetPassword(ResetPasswordRequest request) {
		if (!request.password().equals(request.confirmPassword())) {
			throw new IllegalArgumentException("A nova senha e a confirmação da senha precisam ser iguais.");
		}

		User user = userRepository.findByPasswordResetTokenHash(hashToken(request.token()))
			.orElseThrow(() -> new NotFoundException("O link para redefinição de senha é inválido ou já expirou."));

		if (user.getPasswordResetTokenExpiresAt() == null
			|| user.getPasswordResetTokenExpiresAt().isBefore(OffsetDateTime.now())) {
			clearResetToken(user);
			userRepository.save(user);
			throw new IllegalArgumentException("O link para redefinição de senha expirou. Solicite um novo.");
		}

		tenantAccessService.ensureUserBelongsToCurrentTenant(
			user,
			"Este link de redefinição não pertence ao tenant atual."
		);

		user.setPasswordHash(passwordEncoder.encode(request.password()));
		clearResetToken(user);
		userRepository.save(user);
		auditTrailService.recordUserAction("PASSWORD_RESET_COMPLETED", user, "user-profile", user.getId());

		return new OperationMessageResponse("Senha redefinida com sucesso. Agora você já pode entrar na conta.");
	}

	private void clearResetToken(User user) {
		user.setPasswordResetTokenHash(null);
		user.setPasswordResetTokenExpiresAt(null);
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("Informe um email válido.");
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String hashToken(String rawToken) {
		return sensitiveTokenService.hashToken(rawToken, "Token de redefinição de senha inválido.");
	}
}
