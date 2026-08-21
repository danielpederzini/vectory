package org.vectory.recommendationmanager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommendation-manager.embedding")
public record EmbeddingProperties(
        String baseUrl,
        String path,
        String apiKey,
        String model,
        int dimensions
) {
}
