package com.helpdesk.helpdesk.service;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import com.helpdesk.helpdesk.common.UnauthorizedException;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.repository.UserRepository;
@ExtendWith(MockitoExtension.class)
class AppSessionServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private UserMapper userMapper;

	@Mock
	private TenantAccessService tenantAccessService;

	@Mock
	private AuditTrailService auditTrailService;

	private AppSessionService service;

	@BeforeEach
	void setUp() {
		service = new AppSessionService(userRepository, userMapper, tenantAccessService, auditTrailService);
	}

	@Test
	void shouldKeepIndependentSessionPerTenantWithinSameBrowserSession() {
		UUID tenantAOwnerId = UUID.randomUUID();
		UUID tenantBOwnerId = UUID.randomUUID();
		User tenantAUser = activeUser("a@empresa.com");
		User tenantBUser = activeUser("b@empresa.com");
		MockHttpSession session = new MockHttpSession();

		when(tenantAccessService.getCurrentTenantOwnerUserId())
			.thenReturn(Optional.of(tenantAOwnerId))
			.thenReturn(Optional.of(tenantBOwnerId))
			.thenReturn(Optional.of(tenantAOwnerId))
			.thenReturn(Optional.of(tenantBOwnerId));
		when(userRepository.findById(tenantAUser.getId())).thenReturn(Optional.of(tenantAUser));
		when(userRepository.findById(tenantBUser.getId())).thenReturn(Optional.of(tenantBUser));
		when(tenantAccessService.belongsToCurrentTenant(tenantAUser))
			.thenReturn(true)
			.thenReturn(true);
		when(tenantAccessService.belongsToCurrentTenant(tenantBUser))
			.thenReturn(true)
			.thenReturn(true);

		service.login(tenantAUser, session);
		service.login(tenantBUser, session);
		User meTenantA = service.requireUser(session);
		User meTenantB = service.requireUser(session);

		assertEquals("a@empresa.com", meTenantA.getEmail());
		assertEquals("b@empresa.com", meTenantB.getEmail());
	}

	@Test
	void shouldLogoutOnlyCurrentTenantScopeAndKeepOtherTenantLoggedIn() {
		UUID tenantAOwnerId = UUID.randomUUID();
		UUID tenantBOwnerId = UUID.randomUUID();
		User tenantAUser = activeUser("a@empresa.com");
		User tenantBUser = activeUser("b@empresa.com");
		MockHttpSession session = new MockHttpSession();

		when(tenantAccessService.getCurrentTenantOwnerUserId())
			.thenReturn(Optional.of(tenantAOwnerId))
			.thenReturn(Optional.of(tenantBOwnerId))
			.thenReturn(Optional.of(tenantAOwnerId))
			.thenReturn(Optional.of(tenantAOwnerId))
			.thenReturn(Optional.of(tenantBOwnerId))
			.thenReturn(Optional.of(tenantAOwnerId));
		when(userRepository.findById(tenantAUser.getId())).thenReturn(Optional.of(tenantAUser));
		when(userRepository.findById(tenantBUser.getId())).thenReturn(Optional.of(tenantBUser));
		when(tenantAccessService.belongsToCurrentTenant(tenantAUser))
			.thenReturn(true)
			.thenReturn(true);
		when(tenantAccessService.belongsToCurrentTenant(tenantBUser))
			.thenReturn(true)
			.thenReturn(true);

		service.login(tenantAUser, session);
		service.login(tenantBUser, session);
		service.logout(session);

		User currentTenantBUser = service.requireUser(session);
		assertEquals(tenantBUser.getId(), currentTenantBUser.getId());
		assertThrows(UnauthorizedException.class, () -> service.requireUser(session));
	}

	private User activeUser(String email) {
		User user = new User();
		setField(user, "id", UUID.randomUUID());
		user.setEmail(email);
		user.setFullName("Usuario " + email);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}
	private void setField(Object target, String fieldName, Object value) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Nao foi possivel preparar os dados do teste.", exception);
		}
	}
}
