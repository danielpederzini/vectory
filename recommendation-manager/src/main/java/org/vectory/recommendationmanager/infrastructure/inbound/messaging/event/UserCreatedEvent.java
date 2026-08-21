package org.vectory.recommendationmanager.infrastructure.inbound.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedEvent(
        UUID userId,
        String username,
        String email,
        Instant creationInstant
) {
}
