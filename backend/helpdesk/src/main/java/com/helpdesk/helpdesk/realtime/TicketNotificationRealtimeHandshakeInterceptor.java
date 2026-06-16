package com.helpdesk.helpdesk.realtime;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.service.ScopedUserLookupService;
import com.helpdesk.helpdesk.service.TenantAccessService;
import com.helpdesk.helpdesk.service.TenantExecutionService;
import com.helpdesk.helpdesk.service.TenantResolutionService;
import com.helpdesk.helpdesk.tenant.ResolvedTenant;
import com.helpdesk.helpdesk.util.RequestHostResolver;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class TicketNotificationRealtimeHandshakeInterceptor implements HandshakeInterceptor {

	static final String ATTR_TENANT_OWNER_USER_ID = "ticketRealtimeTenantOwnerUserId";
	static final String ATTR_RECIPIENT_EMAIL = "ticketRealtimeRecipientEmail";

	private final TenantResolutionService tenantResolutionService;
	private final TenantExecutionService tenantExecutionService;
	private final ScopedUserLookupService scopedUserLookupService;
	private final TenantAccessService tenantAccessService;

	public TicketNotificationRealtimeHandshakeInterceptor(
		TenantResolutionService tenantResolutionService,
		TenantExecutionService tenantExecutionService,
		ScopedUserLookupService scopedUserLookupService,
		TenantAccessService tenantAccessService
	) {
		this.tenantResolutionService = tenantResolutionService;
		this.tenantExecutionService = tenantExecutionService;
		this.scopedUserLookupService = scopedUserLookupService;
		this.tenantAccessService = tenantAccessService;
	}

	@Override
	public boolean beforeHandshake(
		ServerHttpRequest request,
		ServerHttpResponse response,
		WebSocketHandler wsHandler,
		Map<String, Object> attributes
	) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) {
			return false;
		}

		HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
		String normalizedEmail = normalizeEmail(httpServletRequest.getParameter("email"));
		if (normalizedEmail == null) {
			return false;
		}

		String resolvedHost = RequestHostResolver.resolveTenantHost(httpServletRequest);
		Optional<ResolvedTenant> resolvedTenant = tenantResolutionService.resolve(resolvedHost);
		if (resolvedTenant.isEmpty() || resolvedTenant.get().ownerUserId() == null) {
			return false;
		}

		UUID tenantOwnerUserId = resolvedTenant.get().ownerUserId();
		boolean userBelongsToTenant = tenantExecutionService.executeInTenantByOwnerUserId(tenantOwnerUserId, () -> {
			User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizedEmail).orElse(null);
			return user != null && tenantAccessService.belongsToCurrentTenant(user);
		});
		if (!userBelongsToTenant) {
			return false;
		}

		attributes.put(ATTR_TENANT_OWNER_USER_ID, tenantOwnerUserId);
		attributes.put(ATTR_RECIPIENT_EMAIL, normalizedEmail);
		return true;
	}

	@Override
	public void afterHandshake(
		ServerHttpRequest request,
		ServerHttpResponse response,
		WebSocketHandler wsHandler,
		Exception exception
	) {
		// No-op
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}

		return email.trim().toLowerCase(Locale.ROOT);
	}
}
