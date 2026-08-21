package org.vectory.recommendationmanager.infrastructure.inbound.messaging.event;

import org.vectory.recommendationmanager.domain.enums.InteractionType;

import java.time.Instant;
import java.util.UUID;

public record InteractionCreatedEvent(
        UUID interactionId,
        UUID postId,
        UUID userId,
        InteractionType type,
        Instant creationInstant
) {
}
