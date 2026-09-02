package org.vectory.recommendationmanager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recommendation-manager")
public record RecommendationProperties(
        InteractionProperties interaction,
        UserEmbeddingProperties userEmbedding,
        CronProperties cron,
        FeedProperties feed
) {
}
