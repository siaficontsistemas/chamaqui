package com.helpdesk.helpdesk.api.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.helpdesk.helpdesk.dto.notification.WebPushPublicKeyResponse;
import com.helpdesk.helpdesk.dto.notification.WebPushSubscriptionRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.WebPushService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications/web-push")
public class WebPushController {
    private final WebPushService webPushService;
    private final AppSessionService appSessionService;
    public WebPushController(WebPushService webPushService, AppSessionService appSessionService) {
        this.webPushService = webPushService;
        this.appSessionService = appSessionService;
    }
    @GetMapping("/public-key")
    public WebPushPublicKeyResponse publicKey(HttpSession session) {
        appSessionService.requireCurrentEmail(session);
        return webPushService.getPublicKey();
    }
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody WebPushSubscriptionRequest request, HttpSession session) {
        webPushService.subscribe(appSessionService.requireCurrentEmail(session), request);
    }
}
