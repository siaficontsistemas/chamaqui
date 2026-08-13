package com.helpdesk.helpdesk.api.push;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.helpdesk.helpdesk.dto.push.WebPushSubscriptionRequest;
import com.helpdesk.helpdesk.service.AppSessionService;
import com.helpdesk.helpdesk.service.WebPushService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/push")
public class WebPushController {

	private final WebPushService webPushService;
	private final AppSessionService appSessionService;

	public WebPushController(WebPushService webPushService, AppSessionService appSessionService) {
		this.webPushService = webPushService;
		this.appSessionService = appSessionService;
	}

	@GetMapping("/public-key")
	public Map<String, String> publicKey() {
		return Map.of("publicKey", webPushService.getPublicKey());
	}

	@PostMapping("/subscriptions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void save(@Valid @RequestBody WebPushSubscriptionRequest request, HttpSession session) {
		webPushService.saveSubscription(appSessionService.requireCurrentEmail(session), request);
	}

	@DeleteMapping("/subscriptions")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@RequestBody WebPushSubscriptionRequest request, HttpSession session) {
		webPushService.deleteSubscription(appSessionService.requireCurrentEmail(session), request.endpoint());
	}
}
