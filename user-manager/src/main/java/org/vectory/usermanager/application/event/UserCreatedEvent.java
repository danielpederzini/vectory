package org.vectory.usermanager.application.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserCreatedEvent(
        UUID userId,
        String username,
        String email,
        Instant creationInstant
) {
}
