package org.vectory.contentmanager.infrastructure.outbound.messaging;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.vectory.contentmanager.domain.enums.AggregateType;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.OutboxEventEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.OutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaOutboxRelay")
class KafkaOutboxRelayTest {

    private static final int BATCH_SIZE = 100;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaOutboxRelay relay;

    private static OutboxEventEntity pendingEvent(String topic, String key) {
        return OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(AggregateType.POST)
                .aggregateId(UUID.randomUUID())
                .topic(topic)
                .messageKey(key)
                .payload(OBJECT_MAPPER.createObjectNode().put("hello", "world"))
                .creationInstant(Instant.now())
                .build();
    }

    @BeforeEach
    void setBatchSize() {
        ReflectionTestUtils.setField(relay, "batchSize", BATCH_SIZE);
    }

    @Test
    @DisplayName("publishes pending events to their topic keyed by message key and marks them published")
    void shouldPublishPendingEventsAndMarkThemPublished() {
        OutboxEventEntity event = pendingEvent(OutboxTopics.POSTS_CREATED, "the-key");
        when(outboxRepository.findByPublicationInstantIsNullOrderByCreationInstantAsc(any(Limit.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(OutboxTopics.POSTS_CREATED, "the-key", event.getPayload().toString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.publishPending();

        verify(kafkaTemplate).send(OutboxTopics.POSTS_CREATED, "the-key", event.getPayload().toString());
        assertThat(event.getPublicationInstant()).isNotNull();
    }

    @Test
    @DisplayName("stops and leaves the event unpublished when the broker send fails")
    void shouldLeaveEventUnpublishedWhenSendFails() {
        OutboxEventEntity event = pendingEvent(OutboxTopics.INTERACTIONS_CREATED, "key");
        when(outboxRepository.findByPublicationInstantIsNullOrderByCreationInstantAsc(any(Limit.class)))
                .thenReturn(List.of(event));
        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenAnswer(invocation -> failed);

        relay.publishPending();

        assertThat(event.getPublicationInstant()).isNull();
    }

    @Test
    @DisplayName("does not touch Kafka when there are no pending events")
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxRepository.findByPublicationInstantIsNullOrderByCreationInstantAsc(any(Limit.class)))
                .thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(String.class));
    }
}
