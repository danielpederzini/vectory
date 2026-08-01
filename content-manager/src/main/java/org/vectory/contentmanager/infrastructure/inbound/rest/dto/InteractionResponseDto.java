package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import lombok.Builder;
import org.vectory.contentmanager.domain.enums.InteractionType;

import java.time.Instant;
import java.util.UUID;

@Builder
public record InteractionResponseDto(
        UUID id,
        UUID postId,
        UUID userId,
        InteractionType type,
        Instant creationInstant
) {
}
