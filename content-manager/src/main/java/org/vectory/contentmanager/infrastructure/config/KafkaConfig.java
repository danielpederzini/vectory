package org.vectory.contentmanager.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxTopics;

@Configuration
@EnableScheduling
public class KafkaConfig {

    @Bean
    public NewTopic postsCreatedTopic() {
        return TopicBuilder.name(OutboxTopics.POSTS_CREATED).build();
    }

    @Bean
    public NewTopic interactionsCreatedTopic() {
        return TopicBuilder.name(OutboxTopics.INTERACTIONS_CREATED).build();
    }
}
