package com.helpdesk.helpdesk.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.helpdesk.domain.CompanyType;
import com.helpdesk.helpdesk.domain.Role;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.dto.whatsapp.SendWhatsappTestMessageRequest;
import com.helpdesk.helpdesk.dto.whatsapp.StartWhatsappSessionRequest;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappOperationResponse;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappQrCodeResponse;
import com.helpdesk.helpdesk.dto.whatsapp.WhatsappSessionStatusResponse;
import com.helpdesk.helpdesk.repository.UserRepository;

@Service
public class WhatsappService {

	private static final Logger logger = LoggerFactory.getLogger(WhatsappService.class);
	private static final String SESSION_PREFIX = "helpdesk-company-";
	private static final String API_KEY_HEADER = "X-Api-Key";

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final RestClient restClient;
	private final UserRepository userRepository;
	private final ScopedUserLookupService scopedUserLookupService;
	private final String apiKey;
	private final String defaultWebhookUrl;

	public WhatsappService(
		@Value("${app.whatsapp.base-url}") String baseUrl,
		@Value("${app.whatsapp.api-key:}") String apiKey,
		@Value("${app.whatsapp.webhook-url:}") String defaultWebhookUrl,
		UserRepository userRepository,
		ScopedUserLookupService scopedUserLookupService
	) {
		this.restClient = RestClient.builder()
			.requestFactory(new SimpleClientHttpRequestFactory())
			.baseUrl(normalizeBaseUrl(baseUrl))
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
		this.apiKey = defaultIfBlank(apiKey);
		this.defaultWebhookUrl = defaultIfBlank(defaultWebhookUrl);
		this.userRepository = userRepository;
		this.scopedUserLookupService = scopedUserLookupService;
	}

	public WhatsappSessionStatusResponse startSession(StartWhatsappSessionRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Informe o administrador da empresa para iniciar a sessão do WhatsApp.");
		}
		User companyOwner = loadCompanyAdmin(request.adminEmail());

		Map<String, Object> payload = new LinkedHashMap<>();
		String webhook = resolveWebhook(request.webhook());
		if (!webhook.isBlank()) {
			payload.put("webhook", webhook);
		}
		payload.put("waitQrCode", request.waitQrCode() == null || request.waitQrCode());

		JsonNode response = requestAuthorized(companyOwner, HttpMethod.POST, "/sessions/{session}/start", payload);
		return toSessionStatusResponse(companyOwner, response, webhook);
	}

	public WhatsappSessionStatusResponse getSessionStatus(String adminEmail) {
		User companyOwner = loadCompanyAdmin(adminEmail);
		JsonNode response = requestAuthorized(companyOwner, HttpMethod.GET, "/sessions/{session}/status", null);
		return toSessionStatusResponse(companyOwner, response, defaultWebhookUrl);
	}

	public WhatsappQrCodeResponse getQrCode(String adminEmail) {
		User companyOwner = loadCompanyAdmin(adminEmail);
		RawWhatsappResponse rawResponse = requestAuthorizedRaw(companyOwner, HttpMethod.GET, "/sessions/{session}/qrcode", null);

		if (rawResponse.isImage()) {
			String qrCode = "data:" + rawResponse.contentTypeValue() + ";base64," +
				Base64.getEncoder().encodeToString(rawResponse.body());
			return new WhatsappQrCodeResponse(
				buildSessionName(companyOwner),
				qrCode,
				"QRCODE",
				"QR Code disponível para leitura.",
				"{\"contentType\":\"" + rawResponse.contentTypeValue() + "\"}"
			);
		}

		JsonNode response = parseJsonBody(rawResponse.bodyAsString());
		String qrCode = firstNonBlank(
			extractText(response, "qrcode"),
			extractText(response, "qrCode"),
			extractText(response, "base64"),
			extractNestedText(response, "qrcode", "base64"),
			extractNestedText(response, "qrCode", "base64")
		);

		return new WhatsappQrCodeResponse(
			buildSessionName(companyOwner),
			qrCode,
			resolveStatus(response),
			resolveMessage(response),
			toJson(response)
		);
	}

	public byte[] getQrCodeImageBytes(String adminEmail) {
		return decodeQrCodeImageBytes(getQrCode(adminEmail).qrCode());
	}

	public String getQrCodeHtmlView(String adminEmail) {
		User companyOwner = loadCompanyAdmin(adminEmail);
		WhatsappQrCodeResponse qrCodeResponse = getQrCode(adminEmail);
		String status = escapeHtml(firstNonBlank(qrCodeResponse.status(), "QRCODE"));
		String message = escapeHtml(firstNonBlank(qrCodeResponse.message(), "Escaneie o QR code com o WhatsApp."));
		String imageSource = toQrCodeImageDataUri(qrCodeResponse.qrCode());
		String companyName = escapeHtml(firstNonBlank(companyOwner.getCompanyName(), companyOwner.getFullName()));

		return """
			<!DOCTYPE html>
			<html lang="pt-BR">
			<head>
			  <meta charset="UTF-8" />
			  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
			  <title>QR Code WhatsApp</title>
			  <style>
			    body {
			      margin: 0;
			      min-height: 100vh;
			      display: flex;
			      align-items: center;
			      justify-content: center;
			      background: #f5f7fb;
			      color: #1f2937;
			      font-family: Arial, sans-serif;
			    }
			    .card {
			      width: min(92vw, 520px);
			      background: #ffffff;
			      border-radius: 16px;
			      box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
			      padding: 24px;
			      text-align: center;
			    }
			    h1 {
			      margin: 0 0 8px;
			      font-size: 24px;
			    }
			    p {
			      margin: 0 0 12px;
			      line-height: 1.5;
			    }
			    .status {
			      display: inline-block;
			      margin-bottom: 16px;
			      padding: 6px 12px;
			      border-radius: 999px;
			      background: #e5eefc;
			      color: #1d4ed8;
			      font-weight: 700;
			      letter-spacing: 0.04em;
			    }
			    img {
			      width: min(78vw, 380px);
			      height: min(78vw, 380px);
			      object-fit: contain;
			      image-rendering: pixelated;
			      background: #ffffff;
			    }
			  </style>
			</head>
			<body>
			  <main class="card">
			    <div class="status">%s</div>
			    <h1>Conectar WhatsApp</h1>
			    <p>Empresa: %s</p>
			    <p>%s</p>
			    <img src="%s" alt="QR Code do WhatsApp" />
			  </main>
			</body>
			</html>
			""".formatted(status, companyName, message, imageSource);
	}

	public WhatsappOperationResponse sendTestMessage(SendWhatsappTestMessageRequest request) {
		return sendMessage(request.adminEmail(), request.phone(), request.message());
	}

	public WhatsappOperationResponse sendMessage(String adminEmail, String phone, String message) {
		return sendMessage(loadCompanyAdmin(adminEmail), phone, message, List.of());
	}

	public WhatsappOperationResponse sendMessage(User companyOwner, String phone, String message) {
		return sendMessage(companyOwner, phone, message, List.of());
	}

	public WhatsappOperationResponse sendMessage(
		User companyOwner,
		String phone,
		String message,
		List<OutboundAttachment> attachments
	) {
		return sendMessage(companyOwner, phone, message, attachments, null);
	}

	public String resolveSentMessageId(WhatsappOperationResponse response) {
		if (response == null || response.data() == null || response.data().isBlank()) {
			return "";
		}
		try {
			return objectMapper.readTree(response.data()).path("messageId").asText("").trim();
		} catch (IOException exception) {
			logger.warn("Não foi possível identificar o ID da mensagem enviada pelo WhatsApp.", exception);
			return "";
		}
	}

	public WhatsappOperationResponse sendMessage(
		User companyOwner,
		String phone,
		String message,
		List<OutboundAttachment> attachments,
		QuotedMessage quotedMessage
	) {
		validateCompanyAdmin(companyOwner);
		String recipient = normalizeRecipient(phone);
		List<OutboundAttachment> normalizedAttachments = normalizeOutboundAttachments(attachments);
		String normalizedMessage = normalizeOptionalMessage(message);

		if (normalizedMessage.isBlank() && normalizedAttachments.isEmpty()) {
			throw new IllegalArgumentException("Informe uma mensagem ou anexe ao menos um arquivo.");
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("phone", recipient);
		if (!normalizedMessage.isBlank()) {
			payload.put("message", normalizedMessage);
		}
		if (!normalizedAttachments.isEmpty()) {
			payload.put("attachments", normalizedAttachments);
		}
		if (quotedMessage != null && !quotedMessage.messageId().isBlank() && !quotedMessage.remoteJid().isBlank()) {
			payload.put("quoted", Map.of(
				"key", Map.of(
					"remoteJid", quotedMessage.remoteJid(),
					"fromMe", false,
					"id", quotedMessage.messageId()
				),
				"message", Map.of("conversation", quotedMessage.text())
			));
		}

		JsonNode response = requestAuthorized(companyOwner, HttpMethod.POST, "/sessions/{session}/messages", payload);
		logger.info(
			"Envio WhatsApp executado: companyOwnerId={}, session={}, recipient={}, bodyPreview={}, attachments={}, response={}",
			companyOwner.getId(),
			buildSessionName(companyOwner),
			recipient,
			previewMessage(normalizedMessage),
			normalizedAttachments.size(),
			toJson(response)
		);
		return new WhatsappOperationResponse(
			buildSessionName(companyOwner),
			"send-message",
			isSuccessResponse(response),
			resolveMessage(response),
			toJson(response)
		);
	}

	public User resolveCompanyAdminBySession(String sessionName) {
		String normalizedSessionName = defaultIfBlank(sessionName).trim();
		if (!normalizedSessionName.startsWith(SESSION_PREFIX)) {
			throw new IllegalArgumentException("Sessão do WhatsApp inválida para o helpdesk.");
		}

		String rawId = normalizedSessionName.substring(SESSION_PREFIX.length());
		try {
			User companyOwner = userRepository.findById(UUID.fromString(rawId))
				.orElseThrow(() -> new IllegalArgumentException("Administrador da sessão do WhatsApp não encontrado."));
			validateCompanyAdmin(companyOwner);
			return companyOwner;
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Sessão do WhatsApp inválida para o helpdesk.");
		}
	}

	public String buildSessionName(User companyOwner) {
		validateCompanyAdmin(companyOwner);
		return SESSION_PREFIX + companyOwner.getId();
	}

	private User loadCompanyAdmin(String adminEmail) {
		User companyOwner = scopedUserLookupService.findUniqueByEmailInCurrentTenant(normalizeEmail(adminEmail))
			.orElseThrow(() -> new IllegalArgumentException("Administrador da empresa não encontrado."));
		validateCompanyAdmin(companyOwner);
		return companyOwner;
	}

	private void validateCompanyAdmin(User companyOwner) {
		if (companyOwner == null || companyOwner.getId() == null) {
			throw new IllegalArgumentException("Administrador da empresa inválido.");
		}
		if (companyOwner.getCompanyType() != CompanyType.RESPONDER) {
			throw new IllegalArgumentException("A conexão do WhatsApp só pode ser feita para empresas que recebem chamados.");
		}
		if (!hasRole(companyOwner, "ADMIN")) {
			throw new IllegalArgumentException("Somente administradores podem conectar o WhatsApp da empresa.");
		}
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getRoles().stream()
			.map(Role::getCode)
			.anyMatch(code -> roleCode.equalsIgnoreCase(code));
	}

	private JsonNode requestAuthorized(User companyOwner, HttpMethod method, String path, Object body) {
		RawWhatsappResponse response = requestAuthorizedRaw(companyOwner, method, path, body);
		return parseJsonBody(response.bodyAsString());
	}

	private RawWhatsappResponse requestAuthorizedRaw(User companyOwner, HttpMethod method, String path, Object body) {
		String sessionName = buildSessionName(companyOwner);

		try {
			RestClient.RequestBodySpec request = restClient.method(method)
				.uri(path, sessionName)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

			if (!apiKey.isBlank()) {
				request.header(API_KEY_HEADER, apiKey);
			}

			if (body != null) {
				request.body(body);
			}

			ResponseEntity<byte[]> response = request.retrieve().toEntity(byte[].class);
			byte[] responseBody = response.getBody() == null ? new byte[0] : response.getBody();
			return new RawWhatsappResponse(response.getHeaders().getContentType(), responseBody);
		} catch (RestClientResponseException exception) {
			throw new IllegalArgumentException(buildIntegrationErrorMessage("serviço do WhatsApp (Baileys)", exception));
		}
	}

	private WhatsappSessionStatusResponse toSessionStatusResponse(User companyOwner, JsonNode response, String webhook) {
		String status = resolveStatus(response);
		return new WhatsappSessionStatusResponse(
			buildSessionName(companyOwner),
			status,
			isConnected(response, status),
			webhook,
			resolveMessage(response),
			toJson(response)
		);
	}

	private boolean isConnected(JsonNode response, String status) {
		if (response != null && response.hasNonNull("connected")) {
			return response.get("connected").asBoolean(false);
		}

		String normalizedStatus = defaultIfBlank(status).trim().toUpperCase(Locale.ROOT);
		return normalizedStatus.equals("CONNECTED")
			|| normalizedStatus.equals("ISLOGGED")
			|| normalizedStatus.equals("INCHAT")
			|| normalizedStatus.equals("OPENING");
	}

	private boolean isSuccessResponse(JsonNode response) {
		String status = resolveStatus(response).toUpperCase(Locale.ROOT);
		return status.isBlank()
			|| status.equals("SUCCESS")
			|| status.equals("SUCESS")
			|| status.equals("OK")
			|| status.equals("CONNECTED");
	}

	private String resolveStatus(JsonNode response) {
		if (response != null && response.has("status") && response.get("status").isBoolean()) {
			return response.get("status").asBoolean(false) ? "CONNECTED" : "DISCONNECTED";
		}

		String explicitStatus = firstNonBlank(
			extractText(response, "status"),
			extractText(response, "state"),
			extractNestedText(response, "response", "status"),
			extractNestedText(response, "response", "state"),
			extractNestedText(response, "session", "status")
		);
		if (!explicitStatus.isBlank()) {
			return explicitStatus;
		}

		if (!firstNonBlank(
			extractText(response, "qrcode"),
			extractText(response, "qrCode"),
			extractText(response, "base64"),
			extractText(response, "urlcode"),
			extractNestedText(response, "qrcode", "base64"),
			extractNestedText(response, "qrCode", "base64")
		).isBlank()) {
			return "QRCODE";
		}

		return "";
	}

	private String resolveMessage(JsonNode response) {
		return firstNonBlank(
			extractText(response, "message"),
			extractText(response, "error"),
			extractNestedText(response, "response", "message"),
			extractNestedText(response, "response", "error")
		);
	}

	private byte[] decodeQrCodeImageBytes(String qrCode) {
		String dataUri = toQrCodeImageDataUri(qrCode);
		int commaIndex = dataUri.indexOf(',');
		if (commaIndex < 0 || commaIndex + 1 >= dataUri.length()) {
			throw new IllegalArgumentException("O serviço de WhatsApp retornou um QRCode inválido para exibição.");
		}
		return Base64.getDecoder().decode(dataUri.substring(commaIndex + 1));
	}

	private String toQrCodeImageDataUri(String qrCode) {
		String normalized = defaultIfBlank(qrCode).trim();
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("O serviço de WhatsApp ainda não disponibilizou um QRCode para a sessão configurada.");
		}

		if (normalized.startsWith("data:image/")) {
			return normalized;
		}

		String compactBase64 = normalized.replaceAll("\\s+", "");
		if (compactBase64.matches("^[A-Za-z0-9+/=]+$")) {
			return "data:image/png;base64," + compactBase64;
		}

		throw new IllegalArgumentException("O serviço de WhatsApp retornou um QRCode em formato não suportado para exibição.");
	}

	private String normalizeBaseUrl(String baseUrl) {
		String normalizedBaseUrl = defaultIfBlank(baseUrl).trim();
		if (normalizedBaseUrl.isBlank()) {
			throw new IllegalArgumentException("Defina APP_WHATSAPP_BASE_URL para usar a integração com WhatsApp.");
		}
		return normalizedBaseUrl.endsWith("/") ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) : normalizedBaseUrl;
	}

	private String normalizeEmail(String email) {
		String normalized = defaultIfBlank(email).trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Informe o e-mail do administrador.");
		}
		return normalized;
	}

	private String normalizeRecipient(String phone) {
		String normalized = defaultIfBlank(phone).trim();
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Informe o telefone de destino.");
		}
		if (normalized.contains("@")) {
			return normalized.endsWith("@c.us")
				? normalized.replace("@c.us", "@s.whatsapp.net")
				: normalized;
		}
		String digitsOnly = normalized.replaceAll("\\D+", "");
		if (digitsOnly.isBlank()) {
			throw new IllegalArgumentException("Informe um telefone de destino valido.");
		}
		return normalizeRecipientDigits(digitsOnly) + "@s.whatsapp.net";
	}

	private String normalizeRecipientDigits(String digitsOnly) {
		if (digitsOnly == null || digitsOnly.isBlank()) {
			throw new IllegalArgumentException("Informe um telefone de destino valido.");
		}

		// Assume Brasil quando o cadastro vier apenas com DDD + numero local.
		if (digitsOnly.length() == 10 || digitsOnly.length() == 11) {
			return "55" + digitsOnly;
		}

		return digitsOnly;
	}

	private String normalizeOptionalMessage(String message) {
		return defaultIfBlank(message).trim();
	}

	private List<OutboundAttachment> normalizeOutboundAttachments(List<OutboundAttachment> attachments) {
		if (attachments == null || attachments.isEmpty()) {
			return List.of();
		}

		return attachments.stream()
			.filter(java.util.Objects::nonNull)
			.map(this::normalizeOutboundAttachment)
			.toList();
	}

	private OutboundAttachment normalizeOutboundAttachment(OutboundAttachment attachment) {
		String originalFileName = defaultIfBlank(attachment.originalFileName()).trim();
		if (originalFileName.isBlank()) {
			originalFileName = "arquivo";
		}

		String contentType = defaultIfBlank(attachment.contentType()).trim().toLowerCase(Locale.ROOT);
		if (contentType.isBlank()) {
			contentType = "application/octet-stream";
		}

		String base64 = defaultIfBlank(attachment.base64()).trim();
		if (base64.isBlank()) {
			throw new IllegalArgumentException("Os anexos do WhatsApp devem conter conteúdo.");
		}

		return new OutboundAttachment(originalFileName, contentType, base64);
	}

	private String resolveWebhook(String webhook) {
		return firstNonBlank(defaultIfBlank(webhook).trim(), defaultWebhookUrl);
	}

	private String extractText(JsonNode node, String fieldName) {
		if (node == null || fieldName == null || fieldName.isBlank() || !node.hasNonNull(fieldName)) {
			return "";
		}
		return node.get(fieldName).asText("");
	}

	private String extractNestedText(JsonNode node, String parentField, String childField) {
		if (node == null || !node.hasNonNull(parentField) || !node.get(parentField).hasNonNull(childField)) {
			return "";
		}
		return node.get(parentField).get(childField).asText("");
	}

	private JsonNode parseJsonBody(String body) {
		try {
			return objectMapper.readTree(defaultIfBlank(body));
		} catch (IOException exception) {
			return objectMapper.createObjectNode();
		}
	}

	private String toJson(JsonNode node) {
		return node == null ? "{}" : node.toString();
	}

	private String buildIntegrationErrorMessage(String operation, RestClientResponseException exception) {
		String body = defaultIfBlank(exception.getResponseBodyAsString()).trim();
		if (body.isBlank()) {
			return "Falha ao comunicar com o " + operation + ".";
		}
		return "Falha ao comunicar com o " + operation + ": " + body;
	}

	private String escapeHtml(String value) {
		return defaultIfBlank(value)
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	private String previewMessage(String message) {
		String normalized = defaultIfBlank(message).trim();
		return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "...";
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return "";
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private String defaultIfBlank(String value) {
		return value == null ? "" : value;
	}

	private record RawWhatsappResponse(MediaType contentType, byte[] body) {
		private boolean isImage() {
			return contentType != null && contentType.getType().equalsIgnoreCase("image");
		}

		private String contentTypeValue() {
			return contentType == null ? MediaType.IMAGE_PNG_VALUE : contentType.toString();
		}

		private String bodyAsString() {
			return new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
		}
	}

	public record OutboundAttachment(
		String originalFileName,
		String contentType,
		String base64
	) {
	}

	public record QuotedMessage(String messageId, String remoteJid, String text) {
	}
}
