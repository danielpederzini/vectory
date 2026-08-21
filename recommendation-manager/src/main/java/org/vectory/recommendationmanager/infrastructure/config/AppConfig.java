package org.vectory.recommendationmanager.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({EmbeddingProperties.class, RecommendationProperties.class})
public class AppConfig {
}
