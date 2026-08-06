package org.vectory.contentmanager.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.vectory.contentmanager.application.event.InteractionCreatedEvent;
import org.vectory.contentmanager.domain.enums.AggregateType;
import org.vectory.contentmanager.domain.enums.InteractionType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InteractionService")
class InteractionServiceTest {

    private static final UUID PERSISTED_INTERACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant PERSISTED_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final InteractionType DEFAULT_TYPE = InteractionType.LIKE;
    private static final String CONSTRAINT_VIOLATION_MESSAGE = "uq_interactions violated";

    @Mock
    private InteractionRepository interactionRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private InteractionService interactionService;

    @Captor
    private ArgumentCaptor<InteractionEntity> interactionEntityCaptor;

    @Captor
    private ArgumentCaptor<InteractionCreatedEvent> interactionCreatedEventCaptor;

    private static InteractionCreationRequestDto buildRequest(InteractionType type) {
        return new InteractionCreationRequestDto(POST_ID, USER_ID, type);
    }

    private static PostEntity buildPostReference() {
        return PostEntity.builder().id(POST_ID).build();
    }

    private PostEntity stubExistingPost() {
        PostEntity postReference = buildPostReference();
        when(postRepository.existsById(POST_ID)).thenReturn(true);
        when(postRepository.getReferenceById(POST_ID)).thenReturn(postReference);
        return postReference;
    }

    private void stubSaveAndFlushToReturnItsArgument() {
        when(interactionRepository.saveAndFlush(any(InteractionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private InteractionEntity capturePersistedEntity() {
        verify(interactionRepository).saveAndFlush(interactionEntityCaptor.capture());
        return interactionEntityCaptor.getValue();
    }

    @Test
    @DisplayName("persists an interaction carrying the submitted user, type and post reference")
    void shouldPersistInteractionBuiltFromRequest() {
        PostEntity postReference = stubExistingPost();
        stubSaveAndFlushToReturnItsArgument();

        Instant before = Instant.now();
        interactionService.create(buildRequest(DEFAULT_TYPE));
        Instant after = Instant.now();

        InteractionEntity persisted = capturePersistedEntity();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getPost()).isSameAs(postReference);
        assertThat(persisted.getUserId()).isEqualTo(USER_ID);
        assertThat(persisted.getType()).isEqualTo(DEFAULT_TYPE);
        assertThat(persisted.getCreationInstant()).isBetween(before, after);
    }

    @ParameterizedTest(name = "type {0}")
    @EnumSource(InteractionType.class)
    @DisplayName("persists an interaction for every supported type")
    void shouldPersistInteractionForEverySupportedType(InteractionType type) {
        stubExistingPost();
        stubSaveAndFlushToReturnItsArgument();

        interactionService.create(buildRequest(type));

        assertThat(capturePersistedEntity().getType()).isEqualTo(type);
    }

    @Test
    @DisplayName("returns the response built from the persisted interaction")
    void shouldReturnResponseBuiltFromPersistedInteraction() {
        InteractionEntity persisted = InteractionEntity.builder()
                .id(PERSISTED_INTERACTION_ID)
                .post(buildPostReference())
                .userId(USER_ID)
                .type(DEFAULT_TYPE)
                .creationInstant(PERSISTED_INSTANT)
                .build();
        stubExistingPost();
        when(interactionRepository.saveAndFlush(any(InteractionEntity.class))).thenReturn(persisted);

        InteractionResponseDto response = interactionService.create(buildRequest(DEFAULT_TYPE));

        assertThat(response.id()).isEqualTo(PERSISTED_INTERACTION_ID);
        assertThat(response.postId()).isEqualTo(POST_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.type()).isEqualTo(DEFAULT_TYPE);
        assertThat(response.creationInstant()).isEqualTo(PERSISTED_INSTANT);
    }

    @Test
    @DisplayName("rejects an interaction on an unknown post without touching the interaction repository")
    void shouldRejectInteractionOnUnknownPostWithoutTouchingTheInteractionRepository() {
        when(postRepository.existsById(POST_ID)).thenReturn(false);

        InteractionCreationRequestDto requestDto = buildRequest(DEFAULT_TYPE);

        assertThatExceptionOfType(PostNotFoundException.class)
                .isThrownBy(() -> interactionService.create(requestDto))
                .satisfies(exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(interactionRepository);
        verify(postRepository, never()).getReferenceById(any(UUID.class));
    }

    @Test
    @DisplayName("translates a uniqueness violation into a duplicate interaction failure")
    void shouldTranslateUniquenessViolationIntoDuplicateInteractionFailure() {
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException(CONSTRAINT_VIOLATION_MESSAGE);
        stubExistingPost();
        when(interactionRepository.saveAndFlush(any(InteractionEntity.class))).thenThrow(cause);

        InteractionCreationRequestDto requestDto = buildRequest(DEFAULT_TYPE);

        assertThatExceptionOfType(DuplicateInteractionException.class)
                .isThrownBy(() -> interactionService.create(requestDto))
                .withCause(cause)
                .satisfies(exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("writes an interactions.created outbox event keyed by the persisted interaction id")
    void shouldWriteInteractionCreatedOutboxEvent() {
        InteractionEntity persisted = InteractionEntity.builder()
                .id(PERSISTED_INTERACTION_ID)
                .post(buildPostReference())
                .userId(USER_ID)
                .type(DEFAULT_TYPE)
                .creationInstant(PERSISTED_INSTANT)
                .build();
        stubExistingPost();
        when(interactionRepository.saveAndFlush(any(InteractionEntity.class))).thenReturn(persisted);

        interactionService.create(buildRequest(DEFAULT_TYPE));

        verify(outboxEventWriter).append(
                eq(AggregateType.INTERACTION),
                eq(PERSISTED_INTERACTION_ID),
                eq(OutboxTopics.INTERACTIONS_CREATED),
                eq(PERSISTED_INTERACTION_ID.toString()),
                interactionCreatedEventCaptor.capture()
        );
        InteractionCreatedEvent event = interactionCreatedEventCaptor.getValue();
        assertThat(event.interactionId()).isEqualTo(PERSISTED_INTERACTION_ID);
        assertThat(event.postId()).isEqualTo(POST_ID);
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.type()).isEqualTo(DEFAULT_TYPE);
        assertThat(event.creationInstant()).isEqualTo(PERSISTED_INSTANT);
    }
}
