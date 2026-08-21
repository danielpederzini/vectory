package org.vectory.recommendationmanager.infrastructure.inbound.messaging.event;

import org.vectory.recommendationmanager.domain.enums.PostMediaType;

public record PostMedia(
        PostMediaType mediaType,
        String mediaUrl
) {
}
