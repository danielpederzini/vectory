package org.vectory.recommendationmanager.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.recommendationmanager.application.port.EmbeddingFactory;
import org.vectory.recommendationmanager.application.port.EmbeddingRequest;
import org.vectory.recommendationmanager.application.port.FetchedMedia;
import org.vectory.recommendationmanager.application.port.MediaFetchPort;
import org.vectory.recommendationmanager.application.util.EmbeddingUtils;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.PostMedia;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.PostEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumePostCreatedEventUseCase implements VoidUseCase<PostCreatedEvent> {

    private final PostEmbeddingRepository postEmbeddingRepository;
    private final EmbeddingFactory embeddingFactory;
    private final MediaFetchPort mediaFetchPort;

    @Override
    @Transactional
    public void execute(PostCreatedEvent event) {
        float[] embedding = embeddingFactory.embed(buildEmbeddingRequest(event));

        PostEmbeddingEntity entity = PostEmbeddingEntity.builder()
                .postId(event.postId())
                .embedding(embedding)
                .creationInstant(event.creationInstant())
                .build();

        postEmbeddingRepository.save(entity);
        log.debug("stored post embedding for post {}", event.postId());
    }

    private EmbeddingRequest buildEmbeddingRequest(PostCreatedEvent event) {
        PostMedia media = event.media();
        if (media != null && media.objectKey() != null) {
            FetchedMedia fetched = mediaFetchPort.fetch(media.objectKey());
            return new EmbeddingRequest(event.text(), fetched.bytes(), fetched.contentType());
        }

        return EmbeddingRequest.ofText(EmbeddingUtils.getPostDescription(event.text(), null));
    }
}
