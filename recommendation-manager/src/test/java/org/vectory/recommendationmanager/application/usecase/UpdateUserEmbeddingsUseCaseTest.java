package org.vectory.recommendationmanager.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.vectory.recommendationmanager.domain.enums.InteractionType;
import org.vectory.recommendationmanager.infrastructure.config.CronProperties;
import org.vectory.recommendationmanager.infrastructure.config.InteractionProperties;
import org.vectory.recommendationmanager.infrastructure.config.RecommendationProperties;
import org.vectory.recommendationmanager.infrastructure.config.UserEmbeddingProperties;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.PostEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.InteractionRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateUserEmbeddingsUseCase")
class UpdateUserEmbeddingsUseCaseTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_POST_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID MISSING_POST_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final Instant CREATION_INSTANT = Instant.parse("2026-01-15T10:15:30Z");
    private static final double ALPHA = 0.2;
    private static final int BATCH_SIZE = 500;
    private static final float OFFSET = 1e-5f;

    private static final float[] AXIS_X = {1.0f, 0.0f};
    private static final float[] AXIS_Y = {0.0f, 1.0f};
    private static final float[] ZERO = {0.0f, 0.0f};

    @Mock
    private InteractionRepository interactionRepository;

    @Mock
    private PostEmbeddingRepository postEmbeddingRepository;

    @Mock
    private UserEmbeddingRepository userEmbeddingRepository;

    private UpdateUserEmbeddingsUseCase useCase;

    @BeforeEach
    void setUp() {
        RecommendationProperties properties = new RecommendationProperties(
                new InteractionProperties(Map.of(
                        InteractionType.VIEW, 0.1,
                        InteractionType.LIKE, 0.4,
                        InteractionType.SAVE, 0.7,
                        InteractionType.SHARE, 1.0)),
                new UserEmbeddingProperties(ALPHA),
                new CronProperties(Duration.ofMinutes(5), BATCH_SIZE));
        useCase = new UpdateUserEmbeddingsUseCase(
                interactionRepository, postEmbeddingRepository, userEmbeddingRepository, properties);
    }

    private static InteractionEntity interaction(UUID postId, InteractionType type) {
        return InteractionEntity.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .postId(postId)
                .type(type)
                .creationInstant(CREATION_INSTANT)
                .build();
    }

    private static UserEmbeddingEntity user(float[] embedding) {
        return UserEmbeddingEntity.builder()
                .userId(USER_ID).embedding(embedding).updatedInstant(CREATION_INSTANT).build();
    }

    private static PostEmbeddingEntity post(UUID postId, float[] embedding) {
        return PostEmbeddingEntity.builder()
                .postId(postId).embedding(embedding).creationInstant(CREATION_INSTANT).build();
    }

    private void givenPending(List<InteractionEntity> interactions) {
        when(interactionRepository.findByProcessedInstantIsNullOrderByCreationInstantAsc(any(Limit.class)))
                .thenReturn(interactions);
    }

    @Test
    @DisplayName("blends the user embedding toward the weighted average and marks interactions processed")
    void shouldBlendAndMarkProcessed() {
        InteractionEntity like = interaction(POST_ID, InteractionType.LIKE);
        UserEmbeddingEntity user = user(AXIS_X.clone());

        givenPending(List.of(like));
        when(postEmbeddingRepository.findAllById(any())).thenReturn(List.of(post(POST_ID, AXIS_Y.clone())));
        when(userEmbeddingRepository.findAllById(any())).thenReturn(List.of(user));

        useCase.execute();

        // blend((1,0),(0,1),0.2) = (0.8,0.2); normalized -> (0.9701, 0.2425)
        assertThat(user.getEmbedding()[0]).isCloseTo(0.97014254f, offset(OFFSET));
        assertThat(user.getEmbedding()[1]).isCloseTo(0.24253564f, offset(OFFSET));
        assertThat(user.getUpdatedInstant()).isAfter(CREATION_INSTANT);
        assertThat(like.getProcessedInstant()).isNotNull();
    }

    @Test
    @DisplayName("weights interactions by type when combining post vectors")
    void shouldWeightByInteractionType() {
        InteractionEntity share = interaction(POST_ID, InteractionType.SHARE);       // weight 1.0 -> (1,0)
        InteractionEntity view = interaction(OTHER_POST_ID, InteractionType.VIEW);   // weight 0.1 -> (0,1)
        UserEmbeddingEntity user = user(ZERO.clone());

        givenPending(List.of(share, view));
        when(postEmbeddingRepository.findAllById(any()))
                .thenReturn(List.of(post(POST_ID, AXIS_X.clone()), post(OTHER_POST_ID, AXIS_Y.clone())));
        when(userEmbeddingRepository.findAllById(any())).thenReturn(List.of(user));

        useCase.execute();

        // SHARE (weight 1.0) dominates VIEW (weight 0.1), so the X axis wins after normalization
        assertThat(user.getEmbedding()[0]).isGreaterThan(user.getEmbedding()[1]);
        assertThat(share.getProcessedInstant()).isNotNull();
        assertThat(view.getProcessedInstant()).isNotNull();
    }

    @Test
    @DisplayName("leaves an interaction unprocessed when its post embedding is not yet available")
    void shouldLeaveInteractionUnprocessedWhenPostMissing() {
        InteractionEntity like = interaction(MISSING_POST_ID, InteractionType.LIKE);
        UserEmbeddingEntity user = user(AXIS_X.clone());

        givenPending(List.of(like));
        when(postEmbeddingRepository.findAllById(any())).thenReturn(List.of());
        when(userEmbeddingRepository.findAllById(any())).thenReturn(List.of(user));

        useCase.execute();

        assertThat(like.getProcessedInstant()).isNull();
        assertThat(user.getEmbedding()).containsExactly(AXIS_X);
    }

    @Test
    @DisplayName("leaves interactions unprocessed when the user embedding does not exist yet")
    void shouldLeaveInteractionUnprocessedWhenUserMissing() {
        InteractionEntity like = interaction(POST_ID, InteractionType.LIKE);

        givenPending(List.of(like));
        when(postEmbeddingRepository.findAllById(any())).thenReturn(List.of(post(POST_ID, AXIS_Y.clone())));
        when(userEmbeddingRepository.findAllById(any())).thenReturn(List.of());

        useCase.execute();

        assertThat(like.getProcessedInstant()).isNull();
    }

    @Test
    @DisplayName("does nothing when there are no pending interactions")
    void shouldDoNothingWhenNoPending() {
        givenPending(List.of());

        useCase.execute();

        verifyNoInteractions(postEmbeddingRepository);
        verifyNoInteractions(userEmbeddingRepository);
    }
}
