package com.helpdesk.helpdesk.realtime;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TicketNotificationRealtimeWebSocketHandler extends TextWebSocketHandler {

	private final TicketNotificationRealtimeSessionRegistry sessionRegistry;

	public TicketNotificationRealtimeWebSocketHandler(TicketNotificationRealtimeSessionRegistry sessionRegistry) {
		this.sessionRegistry = sessionRegistry;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		ConnectionTarget target = resolveTarget(session);
		if (target == null) {
			closeQuietly(session);
			return;
		}

		sessionRegistry.register(target.tenantOwnerUserId(), target.recipientEmail(), session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		ConnectionTarget target = resolveTarget(session);
		if (target == null) {
			return;
		}

		sessionRegistry.unregister(target.tenantOwnerUserId(), target.recipientEmail(), session.getId());
	}

	private ConnectionTarget resolveTarget(WebSocketSession session) {
		if (session == null) {
			return null;
		}

		Map<String, Object> attributes = session.getAttributes();
		Object tenantOwnerUserId = attributes.get(TicketNotificationRealtimeHandshakeInterceptor.ATTR_TENANT_OWNER_USER_ID);
		Object recipientEmail = attributes.get(TicketNotificationRealtimeHandshakeInterceptor.ATTR_RECIPIENT_EMAIL);

		if (!(tenantOwnerUserId instanceof UUID tenantOwnerId) || !(recipientEmail instanceof String email) || email.isBlank()) {
			return null;
		}

		return new ConnectionTarget(tenantOwnerId, email);
	}

	private void closeQuietly(WebSocketSession session) {
		try {
			session.close(CloseStatus.NOT_ACCEPTABLE);
		} catch (Exception ignored) {
			// No-op
		}
	}

	private record ConnectionTarget(UUID tenantOwnerUserId, String recipientEmail) {
	}
}
