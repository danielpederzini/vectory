package org.vectory.recommendationmanager.application.usecase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.recommendationmanager.application.port.EmbeddingFactory;
import org.vectory.recommendationmanager.infrastructure.inbound.messaging.event.UserCreatedEvent;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumeUserCreatedUseCase")
class ConsumeUserCreatedEventUseCaseTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final int DIMENSIONS = 3;
    private static final float SQRT_HALF = 0.70710677f;
    private static final float OFFSET = 1e-5f;

    @Mock
    private UserEmbeddingRepository userEmbeddingRepository;

    @Mock
    private EmbeddingFactory embeddingFactory;

    @InjectMocks
    private ConsumeUserCreatedEventUseCase useCase;

    private static UserCreatedEvent event() {
        return new UserCreatedEvent(USER_ID, "alice", "alice@example.com", CREATION_INSTANT);
    }

    @Test
    @DisplayName("stores a zero vector for the very first user")
    void shouldStoreZeroVectorForFirstUser() {
        when(userEmbeddingRepository.existsById(USER_ID)).thenReturn(false);
        when(userEmbeddingRepository.findAllEmbeddings()).thenReturn(List.of());
        when(embeddingFactory.dimensions()).thenReturn(DIMENSIONS);

        useCase.execute(event());

        ArgumentCaptor<UserEmbeddingEntity> captor = ArgumentCaptor.forClass(UserEmbeddingEntity.class);
        verify(userEmbeddingRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getEmbedding()).containsExactly(0.0f, 0.0f, 0.0f);
    }

    @Test
    @DisplayName("stores the normalized average of existing users for a new user")
    void shouldStoreAverageOfExistingUsers() {
        when(userEmbeddingRepository.existsById(USER_ID)).thenReturn(false);
        when(userEmbeddingRepository.findAllEmbeddings())
                .thenReturn(List.of(new float[]{1.0f, 0.0f, 0.0f}, new float[]{0.0f, 1.0f, 0.0f}));

        useCase.execute(event());

        ArgumentCaptor<UserEmbeddingEntity> captor = ArgumentCaptor.forClass(UserEmbeddingEntity.class);
        verify(userEmbeddingRepository).save(captor.capture());
        float[] stored = captor.getValue().getEmbedding();
        // average is (0.5, 0.5, 0) -> normalized to (0.7071, 0.7071, 0)
        assertThat(stored[0]).isCloseTo(SQRT_HALF, offset(OFFSET));
        assertThat(stored[1]).isCloseTo(SQRT_HALF, offset(OFFSET));
        assertThat(stored[2]).isZero();
    }

    @Test
    @DisplayName("does nothing when the user embedding already exists")
    void shouldSkipWhenUserAlreadyExists() {
        when(userEmbeddingRepository.existsById(USER_ID)).thenReturn(true);

        useCase.execute(event());

        verify(userEmbeddingRepository, never()).save(any());
    }
}
