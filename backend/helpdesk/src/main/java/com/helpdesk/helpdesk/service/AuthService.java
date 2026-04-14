package com.helpdesk.helpdesk.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.auth.AuthResponse;
import com.helpdesk.helpdesk.dto.auth.LoginRequest;
import com.helpdesk.helpdesk.dto.auth.RegisterRequest;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;

	public AuthService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		UserMapper userMapper
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.userMapper = userMapper;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		boolean isAdminRegistration = "admin".equalsIgnoreCase(request.role());
		String normalizedCompanyDocument = normalizeCompanyDocument(request.companyDocument());

		if (userRepository.existsByEmailIgnoreCase(request.email())) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse email.");
		}
		if (userRepository.existsByDocumentNumber(request.documentNumber())) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse CPF/documento.");
		}
		if (isAdminRegistration && blankToNull(request.companyName()) == null) {
			throw new IllegalArgumentException("Informe o nome da empresa para cadastrar um administrador.");
		}
		if (isAdminRegistration && normalizedCompanyDocument == null) {
			throw new IllegalArgumentException("Informe o CNPJ da empresa para cadastrar um administrador.");
		}
		if (normalizedCompanyDocument != null && normalizedCompanyDocument.length() != 14) {
			throw new IllegalArgumentException("Informe um CNPJ válido para a empresa.");
		}
		if (normalizedCompanyDocument != null && userRepository.existsByCompanyDocument(normalizedCompanyDocument)) {
			throw new IllegalArgumentException("Já existe uma conta cadastrada para esse CNPJ.");
		}

		Role role = roleRepository.findByCode(request.role().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		User user = new User();
		user.setFullName(request.fullName().trim());
		user.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
		user.setPhoneNumber(blankToNull(request.phoneNumber()));
		user.setDocumentNumber(request.documentNumber().trim());
		user.setCompanyName(isAdminRegistration ? blankToNull(request.companyName()) : null);
		user.setCompanyDocument(isAdminRegistration ? normalizedCompanyDocument : null);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerified(true);
		user.getRoles().add(role);

		return userMapper.toAuthResponse(userRepository.save(user));
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmailIgnoreCase(request.email().trim())
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Email ou senha inválidos.");
		}

		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new IllegalArgumentException("O usuário não está ativo para acessar o sistema.");
		}

		user.setLastLoginAt(java.time.OffsetDateTime.now());
		return userMapper.toAuthResponse(user);
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String normalizeCompanyDocument(String value) {
		String trimmedValue = blankToNull(value);
		if (trimmedValue == null) {
			return null;
		}
		return trimmedValue.replaceAll("\\D", "");
	}

}
