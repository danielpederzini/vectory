package org.vectory.recommendationmanager.infrastructure.inbound.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.vectory.recommendationmanager.application.usecase.ConsumeInteractionCreatedEventUseCase;
import org.vectory.recommendationmanager.application.usecase.ConsumePostCreatedEventUseCase;
import org.vectory.recommendationmanager.application.usecase.ConsumeUserCreatedEventUseCase;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.InteractionCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.UserCreatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventListener {

    private final ObjectMapper objectMapper;
    private final ConsumePostCreatedEventUseCase consumePostCreatedEventUseCase;
    private final ConsumeUserCreatedEventUseCase consumeUserCreatedEventUseCase;
    private final ConsumeInteractionCreatedEventUseCase consumeInteractionCreatedEventUseCase;

    @KafkaListener(topics = Topics.POSTS_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onPostCreated(String payload) {
        PostCreatedEvent event = objectMapper.readValue(payload, PostCreatedEvent.class);
        consumePostCreatedEventUseCase.execute(event);
    }

    @KafkaListener(topics = Topics.USERS_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onUserCreated(String payload) {
        UserCreatedEvent event = objectMapper.readValue(payload, UserCreatedEvent.class);
        consumeUserCreatedEventUseCase.execute(event);
    }

    @KafkaListener(topics = Topics.INTERACTIONS_CREATED, groupId = "${spring.kafka.consumer.group-id}")
    public void onInteractionCreated(String payload) {
        InteractionCreatedEvent event = objectMapper.readValue(payload, InteractionCreatedEvent.class);
        consumeInteractionCreatedEventUseCase.execute(event);
    }
}
