package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PostResponseDto(
        UUID id,
        UUID authorId,
        String text,
        Instant creationInstant,
        PostMediaResponseDto media
) {
}
