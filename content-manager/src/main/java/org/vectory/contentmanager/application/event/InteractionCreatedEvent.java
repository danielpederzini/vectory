package org.vectory.contentmanager.application.event;

import lombok.Builder;
import org.vectory.contentmanager.domain.enums.InteractionType;

import java.time.Instant;
import java.util.UUID;

@Builder
public record InteractionCreatedEvent(
        UUID interactionId,
        UUID postId,
        UUID userId,
        InteractionType type,
        Instant creationInstant
) {
}
