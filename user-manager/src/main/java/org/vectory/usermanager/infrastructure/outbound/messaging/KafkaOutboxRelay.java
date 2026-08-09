package org.vectory.usermanager.infrastructure.outbound.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.usermanager.infrastructure.outbound.persistence.entity.OutboxEventEntity;
import org.vectory.usermanager.infrastructure.outbound.persistence.repository.OutboxRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutboxRelay implements OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${user-manager.outbox.batch-size:100}")
    private int batchSize;

    @Override
    @Scheduled(fixedDelayString = "${user-manager.outbox.poll-interval:PT1S}")
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> pending =
                outboxRepository.findByPublicationInstantIsNullOrderByCreationInstantAsc(Limit.of(batchSize));

        for (OutboxEventEntity event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload().toString()).get();
                event.setPublicationInstant(Instant.now());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Outbox relay interrupted while publishing event {}", event.getId());
                break;
            } catch (Exception exception) {
                log.error("Failed to publish outbox event {} to topic {}; will retry",
                        event.getId(), event.getTopic(), exception);
                break;
            }
        }
    }
}
