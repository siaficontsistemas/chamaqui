package com.helpdesk.helpdesk.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.common.NotFoundException;
import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientRequest;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientResponse;
import com.helpdesk.helpdesk.dto.client.UpdatePreRegisterClientRequest;
import com.helpdesk.helpdesk.repository.CompanyMembershipRepository;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.util.BrazilianDocumentValidator;

import jakarta.servlet.http.HttpSession;

/**
 * Pré-cadastro de clientes (usuários de empresas clientes) feito pela equipe de suporte da empresa provedora.
 * Somente o administrador do tenant e os funcionários da empresa provedora podem gerenciar esses cadastros.
 */
@Service
public class ClientPreRegistrationService {
	static final String PLACEHOLDER_EMAIL_PREFIX = "pre-cadastro-";
	static final String PLACEHOLDER_EMAIL_DOMAIN = "@invalid.chamaqui";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final TenantAccessService tenantAccessService;
	private final CompanyPartnershipRepository partnershipRepository;
	private final CompanyMembershipRepository companyMembershipRepository;
	private final TicketRepository ticketRepository;
	private final AppSessionService appSessionService;

	public ClientPreRegistrationService(
		UserRepository userRepository,
		RoleRepository roleRepository,
		PasswordEncoder passwordEncoder,
		TenantAccessService tenantAccessService,
		CompanyPartnershipRepository partnershipRepository,
		CompanyMembershipRepository companyMembershipRepository,
		TicketRepository ticketRepository,
		AppSessionService appSessionService
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.tenantAccessService = tenantAccessService;
		this.partnershipRepository = partnershipRepository;
		this.companyMembershipRepository = companyMembershipRepository;
		this.ticketRepository = ticketRepository;
		this.appSessionService = appSessionService;
	}

	/** Lista os pré-cadastros das empresas clientes vinculadas ao tenant atual. */
	@Transactional(readOnly = true)
	public List<PreRegisterClientResponse> list(HttpSession session) {
		requireStaff(session);
		return userRepository.findByPreRegisteredTrueOrderByFullNameAsc().stream()
			.filter(user -> user.getCompanyOwner() != null && isCompanyAllowed(user.getCompanyOwner().getId()))
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public PreRegisterClientResponse create(PreRegisterClientRequest request, HttpSession session) {
		requireStaff(session);
		String phone = normalizeAndValidatePhone(request.phoneNumber());
		if (userRepository.existsByPhoneNumber(phone)) {
			throw new IllegalArgumentException("Já existe um cadastro associado a esse telefone.");
		}
		String email = normalizeOptionalEmail(request.email());
		if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
			throw new IllegalArgumentException("Já existe um cadastro associado a esse e-mail.");
		}
		String document = normalizeOptionalCpf(request.documentNumber());
		if (document != null && userRepository.existsByDocumentNumber(document)) {
			throw new IllegalArgumentException("Já existe um cadastro associado a esse CPF.");
		}
		User company = loadAllowedClientCompany(request.companyOwnerId());
		Role role = roleRepository.findByCode("USER")
			.orElseThrow(() -> new NotFoundException("Perfil de cliente não encontrado."));

		User user = new User();
		user.setFullName(normalizeFullName(request.fullName()));
		user.setPhoneNumber(phone);
		user.setCompanyOwner(company);
		user.setEmail(email != null ? email : buildPlaceholderEmail());
		user.setDocumentNumber(document);
		user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerified(false);
		user.setPreRegistered(true);
		user.setRegistrationTokenHash(null);
		user.setRegistrationTokenExpiresAt(null);
		user.getRoles().add(role);

		return toResponse(userRepository.save(user));
	}

	@Transactional
	public PreRegisterClientResponse update(UUID clientId, UpdatePreRegisterClientRequest request, HttpSession session) {
		requireStaff(session);
		User user = loadManagedPreRegistration(clientId);
		String phone = normalizeAndValidatePhone(request.phoneNumber());
		if (userRepository.existsByPhoneNumberAndIdNot(phone, clientId)) {
			throw new IllegalArgumentException("Já existe outro cadastro associado a esse telefone.");
		}
		String email = normalizeOptionalEmail(request.email());
		if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, clientId)) {
			throw new IllegalArgumentException("Já existe outro cadastro associado a esse e-mail.");
		}
		String document = normalizeOptionalCpf(request.documentNumber());
		if (document != null && userRepository.existsByDocumentNumberAndIdNot(document, clientId)) {
			throw new IllegalArgumentException("Já existe outro cadastro associado a esse CPF.");
		}

		User company = loadAllowedClientCompany(request.companyOwnerId());
		user.setFullName(normalizeFullName(request.fullName()));
		user.setPhoneNumber(phone);
		user.setCompanyOwner(company);
		// Campo ausente (null) mantém o valor atual, para clientes que ainda não enviam e-mail/CPF; texto vazio limpa.
		if (request.email() != null) {
			if (email != null) {
				user.setEmail(email);
			} else if (!isPlaceholderEmail(user.getEmail())) {
				user.setEmail(buildPlaceholderEmail());
			}
		}
		if (request.documentNumber() != null) {
			user.setDocumentNumber(document);
		}
		return toResponse(userRepository.save(user));
	}

	@Transactional
	public void delete(UUID clientId, HttpSession session) {
		requireStaff(session);
		User user = loadManagedPreRegistration(clientId);
		if (ticketRepository.existsByRequesterId(clientId)) {
			throw new IllegalArgumentException(
				"Este pré-cadastro possui chamados associados e não pode ser excluído para preservar o histórico."
			);
		}
		user.getRoles().clear();
		userRepository.delete(user);
	}

	/** Indica se o e-mail é o provisório gerado no pré-cadastro (cliente ainda sem e-mail informado). */
	public static boolean isPlaceholderEmail(String email) {
		return email != null
			&& email.toLowerCase(Locale.ROOT).startsWith(PLACEHOLDER_EMAIL_PREFIX)
			&& email.toLowerCase(Locale.ROOT).endsWith(PLACEHOLDER_EMAIL_DOMAIN);
	}

	private User requireStaff(HttpSession session) {
		User actor = appSessionService.requireUser(session);
		if (!hasRole(actor, "ADMIN") && !hasRole(actor, "EMPLOYEE")) {
			throw new IllegalArgumentException("Somente funcionários e administradores podem gerenciar pré-cadastros.");
		}
		if (!tenantAccessService.hasCurrentTenant()) {
			throw new IllegalArgumentException("O pré-cadastro precisa ser gerenciado dentro do subdomínio da empresa.");
		}
		tenantAccessService.ensureUserBelongsToCurrentTenant(actor, "Esse usuário não pertence ao tenant atual.");
		if (!isProviderStaff(actor)) {
			throw new IllegalArgumentException("Somente a equipe de suporte da empresa pode gerenciar pré-cadastros de clientes.");
		}
		return actor;
	}

	/**
	 * A equipe de suporte é o administrador do tenant e os funcionários ligados diretamente a ele.
	 * Usuários de empresas clientes também podem ter o perfil EMPLOYEE, mas estão ligados à empresa cliente.
	 */
	private boolean isProviderStaff(User actor) {
		UUID tenantOwnerId = tenantAccessService.requireCurrentTenantOwnerUserId();
		if (tenantOwnerId.equals(actor.getId())) {
			return true;
		}
		if (actor.getCompanyOwner() != null && tenantOwnerId.equals(actor.getCompanyOwner().getId())) {
			return true;
		}
		return companyMembershipRepository.existsByUserIdAndCompanyOwnerId(actor.getId(), tenantOwnerId);
	}

	private User loadManagedPreRegistration(UUID clientId) {
		User user = userRepository.findById(clientId)
			.orElseThrow(() -> new NotFoundException("Pré-cadastro não encontrado."));
		if (!user.isPreRegistered()) {
			throw new IllegalArgumentException("Este cliente já concluiu o cadastro e não é mais um pré-cadastro.");
		}
		if (user.getCompanyOwner() == null || !isCompanyAllowed(user.getCompanyOwner().getId())) {
			throw new IllegalArgumentException("Você não tem acesso a este pré-cadastro.");
		}
		return user;
	}

	private User loadAllowedClientCompany(UUID companyOwnerId) {
		User company = userRepository.findAdminCompanyOwnerByIdAndCompanyType(companyOwnerId, CompanyType.REQUESTER)
			.orElseThrow(() -> new NotFoundException("Empresa cliente não encontrada."));
		if (!isCompanyAllowed(company.getId())) {
			throw new IllegalArgumentException("A empresa cliente não está vinculada à empresa atual.");
		}
		return company;
	}

	private boolean isCompanyAllowed(UUID companyId) {
		return tenantAccessService.hasCurrentTenant()
			&& partnershipRepository.existsByCompanyPairAndStatus(
				tenantAccessService.requireCurrentTenantOwnerUserId(),
				companyId,
				CompanyPartnershipStatus.ACCEPTED
			);
	}

	private PreRegisterClientResponse toResponse(User user) {
		User company = user.getCompanyOwner();
		return new PreRegisterClientResponse(
			user.getId(),
			user.getFullName(),
			isPlaceholderEmail(user.getEmail()) ? null : user.getEmail(),
			user.getPhoneNumber(),
			company == null ? null : company.getCompanyName(),
			company == null ? null : company.getId(),
			user.isPreRegistered(),
			user.getDocumentNumber()
		);
	}

	private boolean hasRole(User user, String role) {
		return user.getRoles().stream().anyMatch(item -> role.equalsIgnoreCase(item.getCode()));
	}

	private String buildPlaceholderEmail() {
		return PLACEHOLDER_EMAIL_PREFIX + UUID.randomUUID() + PLACEHOLDER_EMAIL_DOMAIN;
	}

	private String normalizeFullName(String value) {
		String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
		if (normalized.length() < 3) {
			throw new IllegalArgumentException("Informe o nome completo do cliente.");
		}
		return normalized;
	}

	private String normalizeOptionalEmail(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (!EMAIL_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException("Informe um e-mail válido.");
		}
		return normalized;
	}

	private String normalizeOptionalCpf(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String digits = value.replaceAll("\\D", "");
		if (!BrazilianDocumentValidator.isValidCpf(digits)) {
			throw new IllegalArgumentException("Informe um CPF válido.");
		}
		return digits;
	}

	/** Aceita DDD + número (10 ou 11 dígitos), com ou sem o código do país 55. */
	private String normalizeAndValidatePhone(String value) {
		String digits = value == null ? "" : value.replaceAll("\\D", "");
		String normalized = digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)
			? digits.substring(2)
			: digits;
		if (normalized.length() != 10 && normalized.length() != 11) {
			throw new IllegalArgumentException("Informe um telefone válido com DDD, por exemplo (11) 91234-5678.");
		}
		return normalized;
	}
}
