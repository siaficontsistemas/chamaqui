package com.helpdesk.helpdesk.service;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.domain.PlatformAdminUser;
import com.helpdesk.helpdesk.repository.PlatformAdminUserRepository;

@Service
public class PlatformAdminBootstrapService {

	private static final Logger logger = LoggerFactory.getLogger(PlatformAdminBootstrapService.class);

	private final PlatformAdminUserRepository platformAdminUserRepository;
	private final PasswordEncoder passwordEncoder;
	private final String fullName;
	private final String email;
	private final String password;

	public PlatformAdminBootstrapService(
		PlatformAdminUserRepository platformAdminUserRepository,
		PasswordEncoder passwordEncoder,
		@Value("${app.platform-admin.full-name:Administrador da Plataforma}") String fullName,
		@Value("${app.platform-admin.email:}") String email,
		@Value("${app.platform-admin.password:}") String password
	) {
		this.platformAdminUserRepository = platformAdminUserRepository;
		this.passwordEncoder = passwordEncoder;
		this.fullName = fullName == null || fullName.isBlank() ? "Administrador da Plataforma" : fullName.trim();
		this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		this.password = password == null ? "" : password;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void ensurePlatformAdminExists() {
		if (email.isBlank() || password.isBlank()) {
			logger.warn("Administrador da plataforma não inicializado: defina APP_PLATFORM_ADMIN_EMAIL e APP_PLATFORM_ADMIN_PASSWORD.");
			return;
		}

		platformAdminUserRepository.findAll().stream()
			.filter(existingUser -> !email.equalsIgnoreCase(existingUser.getEmail()))
			.forEach(platformAdminUserRepository::delete);

		PlatformAdminUser user = platformAdminUserRepository.findByEmailIgnoreCase(email)
			.orElseGet(PlatformAdminUser::new);
		user.setFullName(fullName);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setActive(true);
		platformAdminUserRepository.save(user);
		logger.info("Administrador da plataforma garantido para o email {}.", email);
	}
}
