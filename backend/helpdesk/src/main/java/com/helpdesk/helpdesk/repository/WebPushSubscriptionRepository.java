package com.helpdesk.helpdesk.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.helpdesk.helpdesk.domain.WebPushSubscription;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, UUID> {
    Optional<WebPushSubscription> findByEndpoint(String endpoint);
    List<WebPushSubscription> findByUserId(UUID userId);
}
