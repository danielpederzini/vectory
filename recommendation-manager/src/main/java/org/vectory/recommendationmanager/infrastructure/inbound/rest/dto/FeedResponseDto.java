package org.vectory.recommendationmanager.infrastructure.inbound.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedResponseDto(
        UUID userId,
        List<FeedItemResponseDto> items,
        int limit,
        int offset,
        boolean hasNext,
        Instant generatedAt
) {
}
