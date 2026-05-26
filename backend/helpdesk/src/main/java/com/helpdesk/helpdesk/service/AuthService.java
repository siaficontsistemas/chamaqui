package com.helpdesk.helpdesk.service;

import java.util.List;
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
import com.helpdesk.helpdesk.dto.auth.RegisterInviteResponse;
import com.helpdesk.helpdesk.dto.auth.RegisterRequest;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.util.BrazilianDocumentValidator;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;
	private final CompanyAccessRequestService companyAccessRequestService;
	private final CnpjLookupService cnpjLookupService;
	private final EmailDomainValidationService emailDomainValidationService;

	public AuthService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		UserMapper userMapper,
		CompanyAccessRequestService companyAccessRequestService,
		CnpjLookupService cnpjLookupService,
		EmailDomainValidationService emailDomainValidationService
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.userMapper = userMapper;
		this.companyAccessRequestService = companyAccessRequestService;
		this.cnpjLookupService = cnpjLookupService;
		this.emailDomainValidationService = emailDomainValidationService;
	}

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		boolean isAdminRegistration = "admin".equalsIgnoreCase(request.role());
		CompanyType companyType = CompanyType.fromValue(request.companyType());
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		emailDomainValidationService.ensurePublicEmailDomainExists(normalizedEmail);
		String normalizedDocumentNumber = normalizeDocumentNumber(request.documentNumber());
		String normalizedInviteToken = blankToNull(request.inviteToken());
		String companyName = blankToNull(request.companyName());
		String normalizedCompanyDocument = normalizeCompanyDocument(request.companyDocument());
		User companyOwner = null;
		User existingUserByEmail = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
		List<User> existingUsersByDocument = findUsersByDocument(normalizedDocumentNumber);
		User upgradeableUser = resolveUpgradeableUser(existingUserByEmail, existingUsersByDocument);

		if (upgradeableUser == null && existingUserByEmail != null) {
			throw new IllegalArgumentException("Já existe um usuário cadastrado com esse email.");
		}
		if (!isAdminRegistration && upgradeableUser == null && !existingUsersByDocument.isEmpty()) {
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
		if (normalizedDocumentNumber == null || !BrazilianDocumentValidator.isValidCpf(normalizedDocumentNumber)) {
			throw new IllegalArgumentException("Informe um CPF válido.");
		}
		if (normalizedCompanyDocument != null && normalizedCompanyDocument.length() != 14) {
			throw new IllegalArgumentException("Informe um CNPJ válido para a empresa.");
		}
		if (normalizedCompanyDocument != null && userRepository.existsAdminCompanyByCompanyDocument(normalizedCompanyDocument)) {
			throw new IllegalArgumentException("Já existe uma conta cadastrada para esse CNPJ.");
		}
		if (isAdminRegistration) {
			cnpjLookupService.ensureCompanyExists(normalizedCompanyDocument);
		}
		if (!isAdminRegistration && request.companyOwnerId() != null && normalizedInviteToken == null) {
			companyOwner = userRepository.findAdminCompanyOwnerByIdAndCompanyType(request.companyOwnerId(), companyType)
				.orElseThrow(() -> new NotFoundException("Empresa não encontrada para o tipo selecionado."));
		}

		Role role = roleRepository.findByCode(request.role().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> new NotFoundException("Perfil não encontrado."));

		User user = upgradeableUser == null ? new User() : upgradeableUser;
		user.setFullName(request.fullName().trim());
		user.setEmail(normalizedEmail);
		user.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));
		user.setDocumentNumber(normalizedDocumentNumber);
		user.setCompanyName(isAdminRegistration ? companyName : null);
		user.setCompanyDocument(isAdminRegistration ? normalizedCompanyDocument : null);
		user.setCompanyType(isAdminRegistration ? companyType : null);
		user.setCompanyOwner(null);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setStatus(resolveInitialStatus(isAdminRegistration, normalizedInviteToken, companyOwner));
		user.setEmailVerified(true);
		user.setSimplified(false);
		user.getRoles().clear();
		user.getRoles().add(role);

		User savedUser = userRepository.save(user);
		if (!isAdminRegistration) {
			if (normalizedInviteToken != null) {
				companyAccessRequestService.acceptAdminInviteDuringRegistration(savedUser, normalizedInviteToken);
			} else if (companyOwner != null) {
				companyAccessRequestService.createPendingRequest(savedUser, companyOwner);
			} else {
				companyAccessRequestService.attachPendingAdminInvitesToRegisteredUser(savedUser);
			}
		}

		return userMapper.toAuthResponse(savedUser);
	}

	@Transactional(readOnly = true)
	public RegisterInviteResponse getRegisterInvite(String inviteToken) {
		return companyAccessRequestService.getRegisterInvite(inviteToken);
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

	private String normalizePhoneNumber(String value) {
		String trimmedValue = blankToNull(value);
		if (trimmedValue == null) {
			return null;
		}

		String digits = trimmedValue.replaceAll("\\D", "");
		if (digits.startsWith("55") && digits.length() == 13) {
			digits = digits.substring(2);
		}

		return digits.isBlank() ? null : digits;
	}

	private List<User> findUsersByDocument(String normalizedDocumentNumber) {
		if (normalizedDocumentNumber == null) {
			return List.of();
		}
		return userRepository.findAllByDocumentNumberOrderByCreatedAtAsc(normalizedDocumentNumber);
	}

	private User resolveUpgradeableUser(User existingUserByEmail, List<User> existingUsersByDocument) {
		if (existingUserByEmail == null && existingUsersByDocument.isEmpty()) {
			return null;
		}
		if (existingUserByEmail != null
			&& existingUsersByDocument.stream()
				.noneMatch(existingUserByDocument -> existingUserByDocument.getId().equals(existingUserByEmail.getId()))
			&& !existingUsersByDocument.isEmpty()) {
			throw new IllegalArgumentException(
				"Já existem cadastros conflitantes para esse email e CPF. Revise os dados informados."
			);
		}

		if (existingUserByEmail != null) {
			return isReusableRegistrationCandidate(existingUserByEmail) ? existingUserByEmail : null;
		}

		if (existingUsersByDocument.size() != 1) {
			return null;
		}

		User candidate = existingUsersByDocument.getFirst();
		return candidate.isSimplified() || isReusableRegistrationCandidate(candidate) ? candidate : null;
	}

	private boolean isReusableRegistrationCandidate(User candidate) {
		return candidate.getStatus() != UserStatus.ACTIVE
			&& candidate.getCompanyOwner() == null
			&& blankToNull(candidate.getCompanyName()) == null
			&& blankToNull(candidate.getCompanyDocument()) == null;
	}

	private UserStatus resolveInitialStatus(boolean isAdminRegistration, String inviteToken, User companyOwner) {
		if (isAdminRegistration || inviteToken != null || companyOwner == null) {
			return UserStatus.ACTIVE;
		}

		return UserStatus.PENDING;
	}

}
