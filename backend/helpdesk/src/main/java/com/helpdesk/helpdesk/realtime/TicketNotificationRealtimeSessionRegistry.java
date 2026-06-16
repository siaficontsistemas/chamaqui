package com.helpdesk.helpdesk.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class TicketNotificationRealtimeSessionRegistry {

	private final ObjectMapper objectMapper;
	private final Map<String, Map<String, WebSocketSession>> sessionsByRecipientKey = new ConcurrentHashMap<>();

	public TicketNotificationRealtimeSessionRegistry(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void register(UUID tenantOwnerUserId, String recipientEmail, WebSocketSession session) {
		if (tenantOwnerUserId == null || recipientEmail == null || recipientEmail.isBlank() || session == null) {
			return;
		}

		sessionsByRecipientKey
			.computeIfAbsent(buildRecipientKey(tenantOwnerUserId, recipientEmail), ignored -> new ConcurrentHashMap<>())
			.put(session.getId(), session);
	}

	public void unregister(UUID tenantOwnerUserId, String recipientEmail, String sessionId) {
		if (tenantOwnerUserId == null || recipientEmail == null || recipientEmail.isBlank() || sessionId == null || sessionId.isBlank()) {
			return;
		}

		Map<String, WebSocketSession> sessions = sessionsByRecipientKey.get(buildRecipientKey(tenantOwnerUserId, recipientEmail));
		if (sessions == null) {
			return;
		}

		sessions.remove(sessionId);
		if (sessions.isEmpty()) {
			sessionsByRecipientKey.remove(buildRecipientKey(tenantOwnerUserId, recipientEmail));
		}
	}

	public void sendToRecipient(UUID tenantOwnerUserId, String recipientEmail, TicketNotificationRealtimeEvent event) {
		if (tenantOwnerUserId == null || recipientEmail == null || recipientEmail.isBlank() || event == null) {
			return;
		}

		Map<String, WebSocketSession> sessions = sessionsByRecipientKey.get(buildRecipientKey(tenantOwnerUserId, recipientEmail));
		if (sessions == null || sessions.isEmpty()) {
			return;
		}

		TextMessage message = toTextMessage(event);
		if (message == null) {
			return;
		}

		sessions.forEach((sessionId, session) -> {
			if (session == null || !session.isOpen()) {
				unregister(tenantOwnerUserId, recipientEmail, sessionId);
				return;
			}

			try {
				synchronized (session) {
					session.sendMessage(message);
				}
			} catch (IOException exception) {
				unregister(tenantOwnerUserId, recipientEmail, sessionId);
			}
		});
	}

	private TextMessage toTextMessage(TicketNotificationRealtimeEvent event) {
		try {
			return new TextMessage(objectMapper.writeValueAsString(event));
		} catch (JsonProcessingException exception) {
			return null;
		}
	}

	private String buildRecipientKey(UUID tenantOwnerUserId, String recipientEmail) {
		return tenantOwnerUserId + "::" + recipientEmail.trim().toLowerCase();
	}
}
