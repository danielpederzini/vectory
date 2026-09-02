package org.vectory.recommendationmanager.infrastructure.config;

import java.time.Duration;

public record FeedProperties(
        int candidateLimit,
        int defaultLimit,
        int maxLimit,
        Duration recencyHalfLife,
        FeedRankingWeights weights
) {
}
