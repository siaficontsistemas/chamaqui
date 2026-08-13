package com.helpdesk.helpdesk.dto.push;

import jakarta.validation.constraints.NotBlank;

public record WebPushSubscriptionRequest(
	@NotBlank String endpoint,
	@NotBlank String p256dh,
	@NotBlank String auth,
	Double expirationTime
) {}
