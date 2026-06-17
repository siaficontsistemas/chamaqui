package com.helpdesk.helpdesk.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Service;

@Service
public class SensitiveTokenService {

	private static final int DEFAULT_TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generateUrlSafeToken() {
		byte[] randomBytes = new byte[DEFAULT_TOKEN_BYTES];
		secureRandom.nextBytes(randomBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
	}

	public String hashToken(String rawToken, String invalidTokenMessage) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new IllegalArgumentException(invalidTokenMessage);
		}

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			byte[] hash = messageDigest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte currentByte : hash) {
				hex.append(String.format("%02x", currentByte));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Não foi possível processar o token informado.", exception);
		}
	}
}
