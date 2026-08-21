package org.vectory.recommendationmanager.infrastructure.config;

import java.time.Duration;

public record CronProperties(Duration fixedDelay, int batchSize) {
}
