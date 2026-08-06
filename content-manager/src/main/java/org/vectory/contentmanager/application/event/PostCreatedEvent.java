package org.vectory.contentmanager.application.event;

import lombok.Builder;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostMediaResponseDto;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PostCreatedEvent(
        UUID postId,
        UUID authorId,
        String text,
        PostMediaResponseDto media,
        Instant creationInstant
) {
}
