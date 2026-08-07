package com.helpdesk.helpdesk.dto.notification;

import jakarta.validation.constraints.NotBlank;

public record WebPushSubscriptionRequest(
    @NotBlank String endpoint,
    @NotBlank String p256dh,
    @NotBlank String auth
) {}
