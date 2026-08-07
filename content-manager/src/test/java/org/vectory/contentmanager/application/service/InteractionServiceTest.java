package org.vectory.contentmanager.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.vectory.contentmanager.application.event.InteractionCreatedEvent;
import org.vectory.contentmanager.application.mapper.InteractionMapper;
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
import static org.mockito.Mockito.mockStatic;
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
    private static final InteractionType DEFAULT_TYPE = InteractionType.LIKE;
    private static final String CONSTRAINT_VIOLATION_MESSAGE = "uq_interactions violated";

    private static final InteractionCreationRequestDto REQUEST =
            new InteractionCreationRequestDto(POST_ID, USER_ID, DEFAULT_TYPE);
    private static final PostEntity POST_REFERENCE = PostEntity.builder().id(POST_ID).build();
    private static final InteractionEntity MAPPED_ENTITY = InteractionEntity.builder().build();
    private static final InteractionEntity SAVED_ENTITY = InteractionEntity.builder().id(PERSISTED_INTERACTION_ID).build();
    private static final InteractionCreatedEvent CREATED_EVENT =
            InteractionCreatedEvent.builder().interactionId(PERSISTED_INTERACTION_ID).build();
    private static final InteractionResponseDto RESPONSE =
            InteractionResponseDto.builder().id(PERSISTED_INTERACTION_ID).build();

    @Mock
    private InteractionRepository interactionRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private InteractionService interactionService;

    private MockedStatic<InteractionMapper> interactionMapper;

    @Captor
    private ArgumentCaptor<UUID> idCaptor;

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    @BeforeEach
    void openMapperMock() {
        interactionMapper = mockStatic(InteractionMapper.class);
    }

    @AfterEach
    void closeMapperMock() {
        interactionMapper.close();
    }

    private void stubHappyPath() {
        when(postRepository.existsById(POST_ID)).thenReturn(true);
        when(postRepository.getReferenceById(POST_ID)).thenReturn(POST_REFERENCE);
        interactionMapper.when(() -> InteractionMapper.toEntity(eq(REQUEST), any(UUID.class), eq(POST_REFERENCE), any(Instant.class)))
                .thenReturn(MAPPED_ENTITY);
        when(interactionRepository.saveAndFlush(MAPPED_ENTITY)).thenReturn(SAVED_ENTITY);
        interactionMapper.when(() -> InteractionMapper.toCreatedEvent(SAVED_ENTITY)).thenReturn(CREATED_EVENT);
        interactionMapper.when(() -> InteractionMapper.toResponseDto(SAVED_ENTITY)).thenReturn(RESPONSE);
    }

    @Test
    @DisplayName("maps the request to an entity with a generated id, post reference and instant, then persists it")
    void shouldMapRequestToEntityAndPersistIt() {
        stubHappyPath();

        Instant before = Instant.now();
        interactionService.create(REQUEST);
        Instant after = Instant.now();

        interactionMapper.verify(() -> InteractionMapper.toEntity(
                eq(REQUEST), idCaptor.capture(), eq(POST_REFERENCE), instantCaptor.capture()));
        assertThat(idCaptor.getValue()).isNotNull();
        assertThat(instantCaptor.getValue()).isBetween(before, after);
        verify(interactionRepository).saveAndFlush(MAPPED_ENTITY);
    }

    @Test
    @DisplayName("returns the response produced by the mapper from the persisted entity")
    void shouldReturnResponseFromMapper() {
        stubHappyPath();

        InteractionResponseDto response = interactionService.create(REQUEST);

        assertThat(response).isSameAs(RESPONSE);
        interactionMapper.verify(() -> InteractionMapper.toResponseDto(SAVED_ENTITY));
    }

    @Test
    @DisplayName("writes an interactions.created outbox event mapped from the persisted entity and keyed by its id")
    void shouldWriteInteractionCreatedOutboxEvent() {
        stubHappyPath();

        interactionService.create(REQUEST);

        interactionMapper.verify(() -> InteractionMapper.toCreatedEvent(SAVED_ENTITY));
        verify(outboxEventWriter).append(
                eq(AggregateType.INTERACTION),
                eq(PERSISTED_INTERACTION_ID),
                eq(OutboxTopics.INTERACTIONS_CREATED),
                eq(PERSISTED_INTERACTION_ID.toString()),
                eq(CREATED_EVENT)
        );
    }

    @Test
    @DisplayName("rejects an interaction on an unknown post without touching the mapper or the interaction repository")
    void shouldRejectInteractionOnUnknownPostWithoutTouchingTheInteractionRepository() {
        when(postRepository.existsById(POST_ID)).thenReturn(false);

        assertThatExceptionOfType(PostNotFoundException.class)
                .isThrownBy(() -> interactionService.create(REQUEST))
                .satisfies(exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(interactionRepository);
        verifyNoInteractions(outboxEventWriter);
        interactionMapper.verifyNoInteractions();
        verify(postRepository, never()).getReferenceById(any(UUID.class));
    }

    @Test
    @DisplayName("translates a uniqueness violation into a duplicate interaction failure without emitting an event")
    void shouldTranslateUniquenessViolationIntoDuplicateInteractionFailure() {
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException(CONSTRAINT_VIOLATION_MESSAGE);
        when(postRepository.existsById(POST_ID)).thenReturn(true);
        when(postRepository.getReferenceById(POST_ID)).thenReturn(POST_REFERENCE);
        interactionMapper.when(() -> InteractionMapper.toEntity(eq(REQUEST), any(UUID.class), eq(POST_REFERENCE), any(Instant.class)))
                .thenReturn(MAPPED_ENTITY);
        when(interactionRepository.saveAndFlush(MAPPED_ENTITY)).thenThrow(cause);

        assertThatExceptionOfType(DuplicateInteractionException.class)
                .isThrownBy(() -> interactionService.create(REQUEST))
                .withCause(cause)
                .satisfies(exception -> assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(outboxEventWriter);
        interactionMapper.verify(() -> InteractionMapper.toCreatedEvent(any()), never());
        interactionMapper.verify(() -> InteractionMapper.toResponseDto(any()), never());
    }
}
