package com.helpdesk.helpdesk.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.helpdesk.helpdesk.common.UnauthorizedException;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;
import com.helpdesk.helpdesk.tenant.TenantContext;

@Service
public class AppAuthTokenService {

	private static final long TOKEN_TTL_SECONDS = 60L * 60L * 12L;
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	private final UserRepository userRepository;
	private final TenantAccessService tenantAccessService;
	private final byte[] signingKey;

	public AppAuthTokenService(
		UserRepository userRepository,
		TenantAccessService tenantAccessService,
		@Value("${app.auth.token-secret:}") String configuredSecret
	) {
		this.userRepository = userRepository;
		this.tenantAccessService = tenantAccessService;
		String effectiveSecret = configuredSecret == null || configuredSecret.isBlank()
			? UUID.randomUUID() + ":" + UUID.randomUUID()
			: configuredSecret.trim();
		this.signingKey = effectiveSecret.getBytes(StandardCharsets.UTF_8);
	}

	public String issueToken(User user) {
		String tenantScope = resolveTenantScope();
		long expiresAtEpochSecond = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
		String payload = user.getId() + "|" + tenantScope + "|" + expiresAtEpochSecond;
		byte[] signature = sign(payload);

		return BASE64_URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
			+ "."
			+ BASE64_URL_ENCODER.encodeToString(signature);
	}

	public User authenticate(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw unauthorized();
		}

		String[] tokenParts = rawToken.trim().split("\\.", 2);
		if (tokenParts.length != 2 || tokenParts[0].isBlank() || tokenParts[1].isBlank()) {
			throw unauthorized();
		}

		String payload = decodePayload(tokenParts[0]);
		byte[] providedSignature = decodeSignature(tokenParts[1]);
		byte[] expectedSignature = sign(payload);

		if (!MessageDigest.isEqual(providedSignature, expectedSignature)) {
			throw unauthorized();
		}

		String[] payloadParts = payload.split("\\|", -1);
		if (payloadParts.length != 3) {
			throw unauthorized();
		}

		UUID userId = parseUserId(payloadParts[0]);
		String tokenTenantScope = normalizeTenantScope(payloadParts[1]);
		long expiresAtEpochSecond = parseExpiration(payloadParts[2]);

		if (expiresAtEpochSecond <= Instant.now().getEpochSecond()) {
			throw unauthorized();
		}
		if (!tokenTenantScope.equals(resolveTenantScope())) {
			throw unauthorized();
		}

		User user = userRepository.findById(userId).orElseThrow(this::unauthorized);
		if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
			throw unauthorized();
		}
		if (!tenantAccessService.belongsToCurrentTenant(user)) {
			throw unauthorized();
		}

		return user;
	}

	private String decodePayload(String encodedPayload) {
		try {
			return new String(BASE64_URL_DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException exception) {
			throw unauthorized();
		}
	}

	private byte[] decodeSignature(String encodedSignature) {
		try {
			return BASE64_URL_DECODER.decode(encodedSignature);
		} catch (IllegalArgumentException exception) {
			throw unauthorized();
		}
	}

	private UUID parseUserId(String rawUserId) {
		try {
			return UUID.fromString(rawUserId);
		} catch (IllegalArgumentException exception) {
			throw unauthorized();
		}
	}

	private long parseExpiration(String rawExpiration) {
		try {
			return Long.parseLong(rawExpiration);
		} catch (NumberFormatException exception) {
			throw unauthorized();
		}
	}

	private byte[] sign(String payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
			return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Não foi possível assinar o token de autenticação do aplicativo.", exception);
		}
	}

	private String resolveTenantScope() {
		ResolvedTenant tenant = TenantContext.get();
		return tenant == null ? "" : normalizeTenantScope(tenant.subdomain());
	}

	private String normalizeTenantScope(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private UnauthorizedException unauthorized() {
		return new UnauthorizedException("Faça login para continuar.");
	}

}
