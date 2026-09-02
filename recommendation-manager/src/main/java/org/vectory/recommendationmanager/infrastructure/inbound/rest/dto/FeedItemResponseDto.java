package org.vectory.recommendationmanager.infrastructure.inbound.rest.dto;

import java.util.UUID;

public record FeedItemResponseDto(UUID postId, double score) {
}
