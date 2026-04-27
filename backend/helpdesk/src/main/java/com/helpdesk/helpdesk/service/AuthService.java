package com.helpdesk.helpdesk.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyType;
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
	private final CompanyAccessRequestService companyAccessRequestService;

	public AuthService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		UserMapper userMapper,
		CompanyAccessRequestService companyAccessRequestService
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.userMapper = userMapper;
		this.companyAccessRequestService = companyAccessRequestService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		boolean isAdminRegistration = "admin".equalsIgnoreCase(request.role());
		CompanyType companyType = CompanyType.fromValue(request.companyType());
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		String normalizedDocumentNumber = normalizeDocumentNumber(request.documentNumber());
		String companyName = blankToNull(request.companyName());
		String normalizedCompanyDocument = normalizeCompanyDocument(request.companyDocument());
		User companyOwner = null;
		User existingUserByEmail = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
		User existingUserByDocument = userRepository.findByDocumentNumber(normalizedDocumentNumber).orElse(null);
		User upgradeableUser = resolveUpgradeableUser(existingUserByEmail, existingUserByDocument);

		if (upgradeableUser == null && existingUserByEmail != null) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse email.");
		}
		if (upgradeableUser == null && existingUserByDocument != null) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse CPF/documento.");
		}
		if (isAdminRegistration && companyName == null) {
			throw new IllegalArgumentException("Informe o nome da empresa para cadastrar um administrador.");
		}
		if (isAdminRegistration && normalizedCompanyDocument == null) {
			throw new IllegalArgumentException("Informe o CNPJ da empresa para cadastrar um administrador.");
		}
		if (isAdminRegistration && companyType == null) {
			throw new IllegalArgumentException("Selecione o tipo da empresa para cadastrar um administrador.");
		}
		if (!isAdminRegistration && companyType == null) {
			throw new IllegalArgumentException("Selecione primeiro se o usuário vai criar ou responder chamados.");
		}
		if (!isAdminRegistration && request.companyOwnerId() == null) {
			throw new IllegalArgumentException("Selecione a empresa à qual esse usuário será vinculado.");
		}
		if (normalizedCompanyDocument != null && normalizedCompanyDocument.length() != 14) {
			throw new IllegalArgumentException("Informe um CNPJ válido para a empresa.");
		}
		if (normalizedCompanyDocument != null && userRepository.existsAdminCompanyByCompanyDocument(normalizedCompanyDocument)) {
			throw new IllegalArgumentException("Já existe uma conta cadastrada para esse CNPJ.");
		}
		if (!isAdminRegistration) {
			companyOwner = userRepository.findAdminCompanyOwnerByIdAndCompanyType(request.companyOwnerId(), companyType)
				.orElseThrow(() -> new NotFoundException("Empresa não encontrada para o tipo selecionado."));
		}

		Role role = roleRepository.findByCode(request.role().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		User user = upgradeableUser == null ? new User() : upgradeableUser;
		user.setFullName(request.fullName().trim());
		user.setEmail(normalizedEmail);
		user.setPhoneNumber(blankToNull(request.phoneNumber()));
		user.setDocumentNumber(normalizedDocumentNumber);
		user.setCompanyName(isAdminRegistration ? companyName : null);
		user.setCompanyDocument(isAdminRegistration ? normalizedCompanyDocument : null);
		user.setCompanyType(isAdminRegistration ? companyType : null);
		user.setCompanyOwner(null);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setStatus(isAdminRegistration ? UserStatus.ACTIVE : UserStatus.PENDING);
		user.setEmailVerified(true);
		user.setSimplified(false);
		user.getRoles().clear();
		user.getRoles().add(role);

		User savedUser = userRepository.save(user);
		if (!isAdminRegistration) {
			companyAccessRequestService.createPendingRequest(savedUser, companyOwner);
		}

		return userMapper.toAuthResponse(savedUser);
	}

	@Transactional
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmailIgnoreCase(request.email().trim())
			.orElseThrow(() -> new NotFoundException("Usuário não encontrado."));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Email ou senha inválidos.");
		}

		if (user.getStatus() == UserStatus.PENDING) {
			throw new IllegalArgumentException(
				"Seu cadastro ainda está aguardando aprovação do administrador da empresa."
			);
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

	private String normalizeDocumentNumber(String value) {
		String trimmedValue = blankToNull(value);
		if (trimmedValue == null) {
			return null;
		}
		return trimmedValue.replaceAll("\\D", "");
	}

	private User resolveUpgradeableUser(User existingUserByEmail, User existingUserByDocument) {
		if (existingUserByEmail == null && existingUserByDocument == null) {
			return null;
		}
		if (existingUserByEmail != null && existingUserByDocument != null
			&& !existingUserByEmail.getId().equals(existingUserByDocument.getId())) {
			throw new IllegalArgumentException(
				"Já existem cadastros conflitantes para esse email e CPF. Revise os dados informados."
			);
		}

		User candidate = existingUserByEmail != null ? existingUserByEmail : existingUserByDocument;
		if (candidate == null) {
			return null;
		}
		return candidate.isSimplified() || isReusableRegistrationCandidate(candidate) ? candidate : null;
	}

	private boolean isReusableRegistrationCandidate(User candidate) {
		return candidate.getStatus() != UserStatus.ACTIVE
			&& candidate.getCompanyOwner() == null
			&& blankToNull(candidate.getCompanyName()) == null
			&& blankToNull(candidate.getCompanyDocument()) == null;
	}

}
