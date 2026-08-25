package org.vectory.contentmanager.application.event;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record PostCreatedEvent(
        UUID postId,
        UUID authorId,
        String text,
        PostMediaEvent media,
        Instant creationInstant
) {
}
