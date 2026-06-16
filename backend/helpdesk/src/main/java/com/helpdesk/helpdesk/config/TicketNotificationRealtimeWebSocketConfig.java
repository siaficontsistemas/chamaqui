package com.helpdesk.helpdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.helpdesk.helpdesk.realtime.TicketNotificationRealtimeHandshakeInterceptor;
import com.helpdesk.helpdesk.realtime.TicketNotificationRealtimeWebSocketHandler;

@Configuration
@EnableWebSocket
public class TicketNotificationRealtimeWebSocketConfig implements WebSocketConfigurer {

	private final TicketNotificationRealtimeWebSocketHandler webSocketHandler;
	private final TicketNotificationRealtimeHandshakeInterceptor handshakeInterceptor;

	public TicketNotificationRealtimeWebSocketConfig(
		TicketNotificationRealtimeWebSocketHandler webSocketHandler,
		TicketNotificationRealtimeHandshakeInterceptor handshakeInterceptor
	) {
		this.webSocketHandler = webSocketHandler;
		this.handshakeInterceptor = handshakeInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(webSocketHandler, "/api/v1/realtime/ticket-notifications")
			.addInterceptors(handshakeInterceptor)
			.setAllowedOriginPatterns("*");
	}
}
