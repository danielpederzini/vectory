package org.vectory.contentmanager.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.contentmanager.application.mapper.InteractionMapper;
import org.vectory.contentmanager.domain.exception.DuplicateInteractionException;
import org.vectory.contentmanager.domain.exception.PostNotFoundException;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionResponseDto;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.PostEntity;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.InteractionRepository;
import org.vectory.contentmanager.infrastructure.outbound.persistence.repository.PostRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InteractionService {

    private final InteractionRepository interactionRepository;
    private final PostRepository postRepository;

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
            return InteractionMapper.toResponseDto(interactionRepository.saveAndFlush(interactionEntity));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateInteractionException(postId, request.userId(), request.type(), exception);
        }
    }
}
