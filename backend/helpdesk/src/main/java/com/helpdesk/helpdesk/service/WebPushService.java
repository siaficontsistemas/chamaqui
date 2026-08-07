package com.helpdesk.helpdesk.service;

import java.security.Security;
import java.util.List;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.helpdesk.domain.User;
import com.helpdesk.helpdesk.domain.WebPushSubscription;
import com.helpdesk.helpdesk.dto.notification.WebPushPublicKeyResponse;
import com.helpdesk.helpdesk.dto.notification.WebPushSubscriptionRequest;
import com.helpdesk.helpdesk.repository.WebPushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

@Service
public class WebPushService {
    private static final Logger logger = LoggerFactory.getLogger(WebPushService.class);
    private final WebPushSubscriptionRepository repository;
    private final ScopedUserLookupService userLookup;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public WebPushService(WebPushSubscriptionRepository repository, ScopedUserLookupService userLookup,
        @Value("${app.web-push.public-key:}") String publicKey,
        @Value("${app.web-push.private-key:}") String privateKey,
        @Value("${app.web-push.subject:mailto:chamaqui@siaficont.com.br}") String subject) {
        this.repository = repository;
        this.userLookup = userLookup;
        this.objectMapper = new ObjectMapper();
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.subject = subject.trim();
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
    }

    public WebPushPublicKeyResponse getPublicKey() {
        return new WebPushPublicKeyResponse(publicKey, isConfigured());
    }

    @Transactional
    public void subscribe(String email, WebPushSubscriptionRequest request) {
        if (!isConfigured()) throw new IllegalStateException("Web Push não está configurado no servidor.");
        User user = userLookup.findUniqueByEmailInCurrentTenant(email)
            .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        WebPushSubscription subscription = repository.findByEndpoint(request.endpoint()).orElseGet(WebPushSubscription::new);
        subscription.setUser(user);
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.p256dh());
        subscription.setAuthSecret(request.auth());
        repository.save(subscription);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendTicketEvent(WebPushTicketEvent event) {
        if (!isConfigured()) return;
        List<WebPushSubscription> subscriptions = repository.findByUserId(event.recipientId());
        for (WebPushSubscription subscription : subscriptions) send(subscription, event);
    }

    private void send(WebPushSubscription subscription, WebPushTicketEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(new Payload(event.title(), event.body(), event.url(), event.tag()));
            Notification notification = new Notification(subscription.getEndpoint(), subscription.getP256dh(),
                subscription.getAuthSecret(), payload);
            HttpResponse response = new PushService(publicKey, privateKey, subject).send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) repository.delete(subscription);
            else if (status >= 400) logger.warn("Web Push recusado com status {} para assinatura {}", status, subscription.getId());
        } catch (Exception exception) {
            logger.warn("Não foi possível enviar Web Push para a assinatura {}: {}", subscription.getId(), exception.getMessage());
        }
    }

    private boolean isConfigured() { return !publicKey.isBlank() && !privateKey.isBlank(); }
    private record Payload(String title, String body, String url, String tag) {}
}
