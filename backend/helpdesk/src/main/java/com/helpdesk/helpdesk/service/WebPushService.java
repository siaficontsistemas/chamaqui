package com.helpdesk.helpdesk.service;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;

import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.WebPushSubscription;
import com.helpdesk.helpdesk.dto.push.WebPushSubscriptionRequest;
import com.helpdesk.helpdesk.repository.WebPushSubscriptionRepository;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@Service
public class WebPushService {

	private static final Logger logger = LoggerFactory.getLogger(WebPushService.class);
	private static final int MAX_ENDPOINT_LENGTH = 4000;

	static {
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.addProvider(new BouncyCastleProvider());
		}
	}

	private final WebPushSubscriptionRepository subscriptionRepository;
	private final ScopedUserLookupService scopedUserLookupService;
	private final String publicKey;
	private final String privateKey;
	private final String subject;

	public WebPushService(
		WebPushSubscriptionRepository subscriptionRepository,
		ScopedUserLookupService scopedUserLookupService,
		@Value("${app.push.public-key:}") String publicKey,
		@Value("${app.push.private-key:}") String privateKey,
		@Value("${app.push.subject:mailto:chamaqui@siaficont.com.br}") String subject
	) {
		this.subscriptionRepository = subscriptionRepository;
		this.scopedUserLookupService = scopedUserLookupService;
		this.publicKey = publicKey == null ? "" : publicKey.trim();
		this.privateKey = privateKey == null ? "" : privateKey.trim();
		this.subject = subject == null ? "mailto:chamaqui@siaficont.com.br" : subject.trim();
	}

	public String getPublicKey() {
		return publicKey;
	}

	@Transactional
	public void saveSubscription(String email, WebPushSubscriptionRequest request) {
		if (!isConfigured()) {
			throw new IllegalStateException("As chaves VAPID do Web Push ainda não foram configuradas no backend.");
		}
		String endpoint = normalizeEndpoint(request.endpoint());
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(email)
			.orElseThrow(() -> new IllegalArgumentException("Usuário autenticado não encontrado."));

		WebPushSubscription subscription = subscriptionRepository.findByEndpoint(endpoint)
			.orElseGet(WebPushSubscription::new);
		subscription.setUser(user);
		subscription.setEndpoint(endpoint);
		subscription.setP256dh(request.p256dh().trim());
		subscription.setAuth(request.auth().trim());
		subscription.setExpirationTime(request.expirationTime());
		subscriptionRepository.save(subscription);
	}

	@Transactional
	public void deleteSubscription(String email, String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return;
		}
		User user = scopedUserLookupService.findUniqueByEmailInCurrentTenant(email)
			.orElseThrow(() -> new IllegalArgumentException("Usuário autenticado não encontrado."));
		subscriptionRepository.deleteByEndpointAndUserId(normalizeEndpoint(endpoint), user.getId());
	}

	@Transactional(readOnly = true)
	public void notifyUser(User recipient, String title, String body, String url) {
		if (!isConfigured() || recipient == null || recipient.getId() == null) {
			return;
		}
		List<PushTarget> targets = subscriptionRepository.findByUserId(recipient.getId()).stream()
			.map(subscription -> new PushTarget(
				subscription.getEndpoint(),
				subscription.getP256dh(),
				subscription.getAuth()
			))
			.toList();
		if (targets.isEmpty()) {
			return;
		}

		String payload = "{\"title\":" + jsonValue(title == null ? "ChamAqui" : title)
			+ ",\"body\":" + jsonValue(body == null ? "Você tem uma nova atualização." : body)
			+ ",\"url\":" + jsonValue(url == null || url.isBlank() ? "/tickets" : url) + "}";

		for (PushTarget target : targets) {
			sendAsync(target, payload);
		}
	}

	private void sendAsync(PushTarget target, String payload) {
		Thread.startVirtualThread(() -> {
			try {
				Subscription subscription = new Subscription(
					target.endpoint(),
					new Subscription.Keys(target.p256dh(), target.auth())
				);
				PushService pushService = new PushService(publicKey, privateKey, subject);
				HttpResponse response = pushService.send(new Notification(subscription, payload));
				int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode >= 400) {
					logger.warn("O provedor de Web Push recusou a notificação: status={}, endpoint={}", statusCode, target.endpoint());
				}
			} catch (GeneralSecurityException | JoseException | RuntimeException | java.io.IOException | java.util.concurrent.ExecutionException | InterruptedException exception) {
				if (exception instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				logger.warn("Falha ao enviar notificação Web Push para {}", target.endpoint(), exception);
			}
		});
	}

	private boolean isConfigured() {
		return !publicKey.isBlank() && !privateKey.isBlank();
	}

	private String jsonValue(String value) {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
			.replace("\r", "\\r").replace("\n", "\\n") + "\"";
	}

	private String normalizeEndpoint(String endpoint) {
		String normalized = endpoint == null ? "" : endpoint.trim();
		if (normalized.isBlank() || normalized.length() > MAX_ENDPOINT_LENGTH) {
			throw new IllegalArgumentException("Endpoint de Web Push inválido.");
		}
		return normalized;
	}

	private record PushTarget(String endpoint, String p256dh, String auth) {}
}
