package org.vectory.contentmanager.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.contentmanager.application.mapper.PostMapper;
import org.vectory.contentmanager.application.port.MediaStoragePort;
import org.vectory.contentmanager.domain.enums.AggregateType;
import org.vectory.contentmanager.domain.exception.MediaNotFoundException;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxEventWriter;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxTopics;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostMedia;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.PostRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final AggregateType AGGREGATE_TYPE = AggregateType.POST;

    private final PostRepository postRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final MediaStoragePort mediaStoragePort;

    @Transactional
    public PostResponseDto create(PostCreationRequestDto postCreationRequest) {
        UUID postId = UUID.randomUUID();
        Instant currentInstant = Instant.now();
        PostEntity postEntity = PostMapper.toEntity(postCreationRequest, postId, currentInstant);

        verifyMediaExists(postEntity.getMedia());

        PostEntity savedPost = postRepository.save(postEntity);

        outboxEventWriter.append(
                AGGREGATE_TYPE,
                savedPost.getId(),
                OutboxTopics.POSTS_CREATED,
                savedPost.getId().toString(),
                PostMapper.toCreatedEvent(savedPost)
        );

        return PostMapper.toResponseDto(savedPost);
    }

    private void verifyMediaExists(PostMedia media) {
        if (media == null || media.getObjectKey() == null) {
            return;
        }
        if (!mediaStoragePort.objectExists(media.getObjectKey())) {
            throw new MediaNotFoundException(media.getObjectKey());
        }
    }
}
