package com.helpdesk.helpdesk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.helpdesk.helpdesk.domain.CompanyPartnershipStatus;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientRequest;
import com.helpdesk.helpdesk.dto.client.PreRegisterClientResponse;
import com.helpdesk.helpdesk.repository.CompanyMembershipRepository;
import com.helpdesk.helpdesk.repository.CompanyPartnershipRepository;
import com.helpdesk.helpdesk.repository.RoleRepository;
import com.helpdesk.helpdesk.repository.TicketRepository;
import com.helpdesk.helpdesk.repository.UserRepository;

import jakarta.servlet.http.HttpSession;

@ExtendWith(MockitoExtension.class)
class ClientPreRegistrationServiceTest {

	@Mock private UserRepository userRepository;
	@Mock private RoleRepository roleRepository;
	@Mock private PasswordEncoder passwordEncoder;
	@Mock private TenantAccessService tenantAccessService;
	@Mock private CompanyPartnershipRepository partnershipRepository;
	@Mock private CompanyMembershipRepository companyMembershipRepository;
	@Mock private TicketRepository ticketRepository;
	@Mock private AppSessionService appSessionService;
	@Mock private HttpSession session;

	private ClientPreRegistrationService service;
	private User tenantOwner;
	private User clientCompany;

	@BeforeEach
	void setUp() {
		service = new ClientPreRegistrationService(
			userRepository,
			roleRepository,
			passwordEncoder,
			tenantAccessService,
			partnershipRepository,
			companyMembershipRepository,
			ticketRepository,
			appSessionService
		);
		tenantOwner = user("Provedora", "ADMIN", null);
		clientCompany = user("Cliente SA", "ADMIN", tenantOwner);

		lenient().when(tenantAccessService.hasCurrentTenant()).thenReturn(true);
		lenient().when(tenantAccessService.requireCurrentTenantOwnerUserId()).thenReturn(tenantOwner.getId());
		lenient().when(partnershipRepository.existsByCompanyPairAndStatus(tenantOwner.getId(), clientCompany.getId(), CompanyPartnershipStatus.ACCEPTED))
			.thenReturn(true);
		lenient().when(userRepository.findAdminCompanyOwnerByIdAndCompanyType(clientCompany.getId(), CompanyType.REQUESTER))
			.thenReturn(Optional.of(clientCompany));
		lenient().when(roleRepository.findByCode("USER")).thenReturn(Optional.of(role("USER")));
		lenient().when(passwordEncoder.encode(any())).thenReturn("hash");
		lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void shouldCreatePreRegistrationWithOptionalEmailAndCpfNormalized() {
		User employee = user("Atendente", "EMPLOYEE", tenantOwner);
		when(appSessionService.requireUser(session)).thenReturn(employee);

		PreRegisterClientResponse response = service.create(
			new PreRegisterClientRequest("  Maria   Souza ", "+55 (11) 91234-5678", clientCompany.getId(), " Maria@Empresa.com.br ", "529.982.247-25"),
			session
		);

		assertEquals("Maria Souza", response.fullName());
		assertEquals("11912345678", response.phoneNumber());
		assertEquals("maria@empresa.com.br", response.email());
		assertEquals("52998224725", response.documentNumber());
		assertTrue(response.preRegistered());
	}

	@Test
	void shouldHidePlaceholderEmailWhenEmailIsNotInformed() {
		when(appSessionService.requireUser(session)).thenReturn(tenantOwner);

		PreRegisterClientResponse response = service.create(
			new PreRegisterClientRequest("João Lima", "11 3456-7890", clientCompany.getId(), "", null),
			session
		);

		assertNull(response.email());
		assertEquals("1134567890", response.phoneNumber());
	}

	@Test
	void shouldRejectInvalidCpfEmailAndPhone() {
		when(appSessionService.requireUser(session)).thenReturn(tenantOwner);

		IllegalArgumentException invalidCpf = assertThrows(IllegalArgumentException.class, () -> service.create(
			new PreRegisterClientRequest("João Lima", "11912345678", clientCompany.getId(), null, "111.111.111-11"), session));
		IllegalArgumentException invalidEmail = assertThrows(IllegalArgumentException.class, () -> service.create(
			new PreRegisterClientRequest("João Lima", "11912345678", clientCompany.getId(), "joao@", null), session));
		IllegalArgumentException invalidPhone = assertThrows(IllegalArgumentException.class, () -> service.create(
			new PreRegisterClientRequest("João Lima", "91234", clientCompany.getId(), null, null), session));

		assertEquals("Informe um CPF válido.", invalidCpf.getMessage());
		assertEquals("Informe um e-mail válido.", invalidEmail.getMessage());
		assertTrue(invalidPhone.getMessage().startsWith("Informe um telefone válido"));
		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	void shouldRejectClientCompanyEmployee() {
		// Usuários de empresas clientes também recebem o perfil EMPLOYEE, mas não fazem parte do suporte.
		User clientEmployee = user("Funcionário do cliente", "EMPLOYEE", clientCompany);
		when(appSessionService.requireUser(session)).thenReturn(clientEmployee);
		when(companyMembershipRepository.existsByUserIdAndCompanyOwnerId(clientEmployee.getId(), tenantOwner.getId()))
			.thenReturn(false);

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.list(session));

		assertEquals("Somente a equipe de suporte da empresa pode gerenciar pré-cadastros de clientes.", exception.getMessage());
	}

	@Test
	void shouldListOnlyPreRegistrationsFromLinkedClientCompanies() {
		User otherCompany = user("Outra", "ADMIN", null);
		User linked = user("Ana", "USER", clientCompany);
		User unlinked = user("Bia", "USER", otherCompany);
		when(appSessionService.requireUser(session)).thenReturn(tenantOwner);
		when(userRepository.findByPreRegisteredTrueOrderByFullNameAsc()).thenReturn(List.of(linked, unlinked));

		List<PreRegisterClientResponse> responses = service.list(session);

		assertEquals(1, responses.size());
		assertEquals("Ana", responses.get(0).fullName());
	}

	private User user(String name, String roleCode, User companyOwner) {
		User user = new User();
		setField(user, "id", UUID.randomUUID());
		user.setFullName(name);
		user.setCompanyName(name);
		user.setCompanyOwner(companyOwner);
		user.setPreRegistered("USER".equals(roleCode));
		user.getRoles().add(role(roleCode));
		return user;
	}

	private Role role(String code) {
		Role role = new Role();
		setField(role, "id", UUID.randomUUID());
		setField(role, "code", code);
		setField(role, "name", code);
		return role;
	}

	private void setField(Object target, String fieldName, Object value) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Não foi possível preparar os dados do teste.", exception);
		}
	}
}
