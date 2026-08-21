package org.vectory.recommendationmanager.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.recommendationmanager.application.port.EmbeddingFactory;
import org.vectory.recommendationmanager.domain.util.VectorUtils;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.UserCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumeUserCreatedEventUseCase implements VoidUseCase<UserCreatedEvent> {

    private final UserEmbeddingRepository userEmbeddingRepository;
    private final EmbeddingFactory embeddingFactory;

    @Override
    @Transactional
    public void execute(UserCreatedEvent event) {
        if (userEmbeddingRepository.existsById(event.userId())) {
            log.debug("user embedding already exists for user {}; skipping", event.userId());
            return;
        }

        float[] embedding = coldStartEmbedding();

        UserEmbeddingEntity entity = UserEmbeddingEntity.builder()
                .userId(event.userId())
                .embedding(embedding)
                .updatedInstant(event.creationInstant())
                .build();

        userEmbeddingRepository.save(entity);
        log.debug("stored cold-start embedding for user {}", event.userId());
    }

    private float[] coldStartEmbedding() {
        List<float[]> existing = userEmbeddingRepository.findAllEmbeddings();
        if (existing.isEmpty()) {
            return VectorUtils.zeros(embeddingFactory.dimensions());
        }
        return VectorUtils.normalize(VectorUtils.average(existing));
    }
}
