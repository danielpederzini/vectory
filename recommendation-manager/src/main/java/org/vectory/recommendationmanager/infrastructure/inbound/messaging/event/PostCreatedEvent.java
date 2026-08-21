package org.vectory.recommendationmanager.infrastructure.inbound.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record PostCreatedEvent(
        UUID postId,
        UUID authorId,
        String text,
        PostMedia media,
        Instant creationInstant
) {
}
