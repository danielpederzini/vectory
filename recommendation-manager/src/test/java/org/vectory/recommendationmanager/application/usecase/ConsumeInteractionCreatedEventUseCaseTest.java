package org.vectory.recommendationmanager.application.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.recommendationmanager.domain.enums.InteractionType;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.InteractionCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.InteractionRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumeInteractionCreatedUseCase")
class ConsumeInteractionCreatedEventUseCaseTest {

    private static final UUID INTERACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");

    @Mock
    private InteractionRepository interactionRepository;

    @InjectMocks
    private ConsumeInteractionCreatedEventUseCase useCase;

    private static InteractionCreatedEvent event() {
        return new InteractionCreatedEvent(INTERACTION_ID, POST_ID, USER_ID, InteractionType.LIKE, CREATION_INSTANT);
    }

    @Test
    @DisplayName("stores a new, unprocessed interaction row")
    void shouldStoreUnprocessedInteraction() {
        when(interactionRepository.existsById(INTERACTION_ID)).thenReturn(false);

        useCase.execute(event());

        ArgumentCaptor<InteractionEntity> captor = ArgumentCaptor.forClass(InteractionEntity.class);
        verify(interactionRepository).save(captor.capture());
        InteractionEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(INTERACTION_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        assertThat(saved.getType()).isEqualTo(InteractionType.LIKE);
        assertThat(saved.getCreationInstant()).isEqualTo(CREATION_INSTANT);
        assertThat(saved.getProcessedInstant()).isNull();
    }

    @Test
    @DisplayName("ignores a duplicate interaction id")
    void shouldIgnoreDuplicate() {
        when(interactionRepository.existsById(INTERACTION_ID)).thenReturn(true);

        useCase.execute(event());

        verify(interactionRepository, never()).save(any());
    }
}
