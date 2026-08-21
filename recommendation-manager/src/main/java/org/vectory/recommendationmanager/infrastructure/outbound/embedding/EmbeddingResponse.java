package org.vectory.recommendationmanager.infrastructure.outbound.embedding;

import java.util.List;

public record EmbeddingResponse(List<EmbeddingItem> data) {
}
