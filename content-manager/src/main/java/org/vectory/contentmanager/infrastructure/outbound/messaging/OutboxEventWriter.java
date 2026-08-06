package org.vectory.contentmanager.infrastructure.outbound.messaging;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.vectory.contentmanager.domain.enums.AggregateType;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.OutboxEventEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.OutboxRepository;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void append(AggregateType aggregateType, UUID aggregateId, String topic, String messageKey, Object payload) {
        OutboxEventEntity event = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .topic(topic)
                .messageKey(messageKey)
                .payload(objectMapper.valueToTree(payload))
                .creationInstant(Instant.now())
                .build();
        outboxRepository.save(event);
    }
}
