package org.vectory.contentmanager.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.contentmanager.application.event.InteractionCreatedEvent;
import org.vectory.contentmanager.application.mapper.InteractionMapper;
import org.vectory.contentmanager.domain.enums.AggregateType;
import org.vectory.contentmanager.domain.exception.DuplicateInteractionException;
import org.vectory.contentmanager.domain.exception.PostNotFoundException;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxEventWriter;
import org.vectory.contentmanager.infrastructure.outbound.messaging.OutboxTopics;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.InteractionRepository;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.PostRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InteractionService {

    private static final AggregateType AGGREGATE_TYPE = AggregateType.INTERACTION;

    private final InteractionRepository interactionRepository;
    private final PostRepository postRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional
    public InteractionResponseDto create(InteractionCreationRequestDto request) {
        UUID postId = request.postId();
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException(postId);
        }

        UUID interactionId = UUID.randomUUID();
        PostEntity postEntity = postRepository.getReferenceById(postId);
        Instant currentInstant = Instant.now();

        InteractionEntity interactionEntity = InteractionMapper.toEntity(
                request, interactionId, postEntity, currentInstant
        );

        try {
            InteractionResponseDto response =
                    InteractionMapper.toResponseDto(interactionRepository.saveAndFlush(interactionEntity));

            InteractionCreatedEvent event = InteractionCreatedEvent.builder()
                    .interactionId(response.id())
                    .postId(response.postId())
                    .userId(response.userId())
                    .type(response.type())
                    .creationInstant(response.creationInstant())
                    .build();
            outboxEventWriter.append(
                    AGGREGATE_TYPE, response.id(), OutboxTopics.INTERACTIONS_CREATED, response.id().toString(), event
            );

            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateInteractionException(postId, request.userId(), request.type(), exception);
        }
    }
}
