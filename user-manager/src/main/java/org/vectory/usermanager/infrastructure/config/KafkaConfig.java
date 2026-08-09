package org.vectory.usermanager.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.vectory.usermanager.infrastructure.outbound.messaging.OutboxTopics;

@Configuration
@EnableScheduling
public class KafkaConfig {

    @Bean
    public NewTopic usersCreatedTopic() {
        return TopicBuilder.name(OutboxTopics.USERS_CREATED).build();
    }
}
