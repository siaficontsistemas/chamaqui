package com.helpdesk.helpdesk.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.helpdesk.helpdesk.common.UnauthorizedException;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.UserStatus;
import com.helpdesk.helpdesk.repository.UserRepository;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;
import com.helpdesk.helpdesk.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class AppAuthTokenServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private TenantAccessService tenantAccessService;

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Test
	void authenticateReturnsUserWhenTokenMatchesCurrentTenant() {
		AppAuthTokenService service = new AppAuthTokenService(
			userRepository,
			tenantAccessService,
			"tenant-scoped-secret"
		);
		User user = createActiveUser();
		TenantContext.set(new ResolvedTenant(null, null, "Lopes", "RESPONDER", "lopesconsultoria", "tenant_lopes", null, null));
		String token = service.issueToken(user);

		when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
		when(tenantAccessService.belongsToCurrentTenant(user)).thenReturn(true);

		User authenticatedUser = service.authenticate(token);

		assertSame(user, authenticatedUser);
		verify(userRepository).findById(user.getId());
		verify(tenantAccessService).belongsToCurrentTenant(user);
	}

	@Test
	void authenticateRejectsTokenFromAnotherTenant() {
		AppAuthTokenService service = new AppAuthTokenService(
			userRepository,
			tenantAccessService,
			"tenant-scoped-secret"
		);
		User user = createActiveUser();
		TenantContext.set(new ResolvedTenant(null, null, "Lopes", "RESPONDER", "lopesconsultoria", "tenant_lopes", null, null));
		String token = service.issueToken(user);

		TenantContext.set(new ResolvedTenant(null, null, "Siafi", "RESPONDER", "siaficont", "tenant_siafi", null, null));

		UnauthorizedException exception = assertThrows(UnauthorizedException.class, () -> service.authenticate(token));
		assertEquals("Faça login para continuar.", exception.getMessage());
	}

	private User createActiveUser() {
		User user = new User();
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		user.setEmail("usuario@teste.com");
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}
}
