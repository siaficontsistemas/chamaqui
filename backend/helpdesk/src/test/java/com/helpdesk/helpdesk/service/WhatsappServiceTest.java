package com.helpdesk.helpdesk.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class WhatsappServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer httpServer;

	@AfterEach
	void tearDown() {
		if (httpServer != null) {
			httpServer.stop(0);
		}
	}

	@Test
	void shouldPrefixBrazilCountryCodeWhenPhoneHasOnlyAreaCodeAndLocalNumber() throws Exception {
		AtomicReference<String> requestBody = new AtomicReference<>("");
		WhatsappService whatsappService = createService(requestBody);

		whatsappService.sendMessage(companyOwner(), "77997006654", "Teste");

		JsonNode payload = objectMapper.readTree(requestBody.get());
		assertEquals("5577997006654@s.whatsapp.net", payload.get("phone").asText());
		assertEquals("Teste", payload.get("message").asText());
	}

	@Test
	void shouldKeepLidRecipientUntouched() throws Exception {
		AtomicReference<String> requestBody = new AtomicReference<>("");
		WhatsappService whatsappService = createService(requestBody);

		whatsappService.sendMessage(companyOwner(), "209607456719099@lid", "Teste");

		JsonNode payload = objectMapper.readTree(requestBody.get());
		assertEquals("209607456719099@lid", payload.get("phone").asText());
	}

	private WhatsappService createService(AtomicReference<String> requestBody) throws IOException {
		httpServer = HttpServer.create(new InetSocketAddress(0), 0);
		httpServer.createContext("/sessions/helpdesk-company-" + companyOwner().getId() + "/messages", exchange -> {
			try (exchange) {
				requestBody.set(readBody(exchange));
				byte[] responseBody = """
					{"status":"SUCCESS","message":"Mensagem enviada com sucesso."}
					""".trim().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, responseBody.length);
				try (OutputStream outputStream = exchange.getResponseBody()) {
					outputStream.write(responseBody);
				}
			}
		});
		httpServer.start();

		return new WhatsappService(
			"http://127.0.0.1:" + httpServer.getAddress().getPort(),
			"",
			"",
			Mockito.mock(com.helpdesk.helpdesk.repository.UserRepository.class),
			Mockito.mock(ScopedUserLookupService.class)
		);
	}

	private String readBody(HttpExchange exchange) throws IOException {
		return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
	}

	private User companyOwner() {
		User user = new User();
		setField(user, "id", UUID.fromString("88e68d7f-a7b1-4deb-94a3-aaa024d1d202"));
		user.setEmail("admin@empresa.com");
		user.setFullName("Empresa Admin");
		user.setCompanyName("Empresa Admin");
		user.setCompanyType(CompanyType.RESPONDER);
		user.getRoles().add(role("ADMIN"));
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
			throw new IllegalStateException("Nao foi possivel preparar os dados do teste.", exception);
		}
	}
}
