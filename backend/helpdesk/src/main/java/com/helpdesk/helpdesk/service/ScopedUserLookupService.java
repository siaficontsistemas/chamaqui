package com.helpdesk.helpdesk.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class ScopedUserLookupService {

	private final UserRepository userRepository;
	private final TenantAccessService tenantAccessService;

	public ScopedUserLookupService(UserRepository userRepository, TenantAccessService tenantAccessService) {
		this.userRepository = userRepository;
		this.tenantAccessService = tenantAccessService;
	}

	@Transactional(readOnly = true)
	public Optional<User> findUniqueByEmailInCurrentTenant(String email) {
		List<User> matches = findAllByEmailInCurrentTenant(email);
		if (matches.isEmpty()) {
			return Optional.empty();
		}
		if (matches.size() > 1) {
			throw new IllegalStateException("Existe mais de um usuário com esse email no subdomínio atual.");
		}
		return Optional.of(matches.getFirst());
	}

	@Transactional(readOnly = true)
	public List<User> findAllByEmailInCurrentTenant(String email) {
		String normalizedEmail = normalizeEmail(email);
		if (normalizedEmail == null) {
			return List.of();
		}

		return userRepository.findAllByEmailIgnoreCaseOrderByCreatedAtAsc(normalizedEmail).stream()
			.filter(tenantAccessService::belongsToCurrentTenant)
			.toList();
	}

	@Transactional(readOnly = true)
	public Optional<User> findUniqueStandaloneByEmail(String email) {
		List<User> matches = findStandaloneUsersByEmail(email);
		if (matches.isEmpty()) {
			return Optional.empty();
		}
		if (matches.size() > 1) {
			throw new IllegalStateException("Existe mais de um usuário fora de tenant com esse email.");
		}
		return Optional.of(matches.getFirst());
	}

	@Transactional(readOnly = true)
	public List<User> findStandaloneUsersByEmail(String email) {
		String normalizedEmail = normalizeEmail(email);
		if (normalizedEmail == null) {
			return List.of();
		}

		return userRepository.findAllByEmailIgnoreCaseOrderByCreatedAtAsc(normalizedEmail).stream()
			.filter(user -> tenantAccessService.findPrimaryCompanyForUser(user).isEmpty())
			.toList();
	}

	@Transactional(readOnly = true)
	public List<User> findAllByEmailAcrossPlatform(String email) {
		String normalizedEmail = normalizeEmail(email);
		if (normalizedEmail == null) {
			return List.of();
		}

		return userRepository.findAllByEmailIgnoreCaseOrderByCreatedAtAsc(normalizedEmail);
	}

	@Transactional(readOnly = true)
	public Optional<User> resolveLoginCandidate(String email) {
		String normalizedEmail = normalizeEmail(email);
		if (normalizedEmail == null) {
			return Optional.empty();
		}

		if (tenantAccessService.hasCurrentTenant()) {
			return findUniqueByEmailInCurrentTenant(normalizedEmail);
		}

		List<User> matches = findAllByEmailAcrossPlatform(normalizedEmail);
		if (matches.isEmpty()) {
			return Optional.empty();
		}
		if (matches.size() == 1) {
			return Optional.of(matches.getFirst());
		}

		List<User> standaloneMatches = matches.stream()
			.filter(user -> tenantAccessService.findPrimaryCompanyForUser(user).isEmpty())
			.toList();
		if (standaloneMatches.size() == 1) {
			return Optional.of(standaloneMatches.getFirst());
		}
		if (standaloneMatches.size() > 1) {
			throw new IllegalArgumentException("Esse email está vinculado a mais de uma conta fora de subdomínio.");
		}

		throw new IllegalArgumentException(
			"Esse email está cadastrado em mais de uma empresa. Acesse pelo subdomínio correto para entrar."
		);
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
