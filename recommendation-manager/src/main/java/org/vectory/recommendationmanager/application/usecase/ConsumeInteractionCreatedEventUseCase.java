package org.vectory.recommendationmanager.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.InteractionCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.InteractionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumeInteractionCreatedEventUseCase implements VoidUseCase<InteractionCreatedEvent> {

    private final InteractionRepository interactionRepository;

    @Override
    @Transactional
    public void execute(InteractionCreatedEvent event) {
        if (interactionRepository.existsById(event.interactionId())) {
            log.debug("interaction {} already stored; skipping", event.interactionId());
            return;
        }

        InteractionEntity entity = InteractionEntity.builder()
                .id(event.interactionId())
                .userId(event.userId())
                .postId(event.postId())
                .type(event.type())
                .creationInstant(event.creationInstant())
                .build();

        interactionRepository.save(entity);
        log.debug("stored interaction {} for user {} on post {}",
                event.interactionId(), event.userId(), event.postId());
    }
}
