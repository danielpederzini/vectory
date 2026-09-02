package org.vectory.recommendationmanager.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.recommendationmanager.domain.enums.InteractionType;
import org.vectory.recommendationmanager.infrastructure.config.CronProperties;
import org.vectory.recommendationmanager.infrastructure.config.FeedProperties;
import org.vectory.recommendationmanager.infrastructure.config.FeedRankingWeights;
import org.vectory.recommendationmanager.infrastructure.config.InteractionProperties;
import org.vectory.recommendationmanager.infrastructure.config.RecommendationProperties;
import org.vectory.recommendationmanager.infrastructure.config.UserEmbeddingProperties;
import org.vectory.recommendationmanager.infrastructure.inbound.rest.dto.FeedResponseDto;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.FeedCandidate;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.InteractionRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostPopularity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenerateFeedUseCase")
class GenerateFeedUseCaseTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID POST_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID POST_B = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private UserEmbeddingRepository userEmbeddingRepository;
    @Mock private PostEmbeddingRepository postEmbeddingRepository;
    @Mock private InteractionRepository interactionRepository;

    private GenerateFeedUseCase useCase;

    @BeforeEach
    void setUp() {
        RecommendationProperties properties = new RecommendationProperties(
                new InteractionProperties(Map.of(InteractionType.LIKE, 1.0)),
                new UserEmbeddingProperties(0.2),
                new CronProperties(Duration.ofMinutes(5), 500),
                new FeedProperties(500, 20, 50, Duration.ofDays(7), new FeedRankingWeights(0.65, 0.25, 0.10)));
        useCase = new GenerateFeedUseCase(userEmbeddingRepository, postEmbeddingRepository, interactionRepository, properties);
    }

    @Test
    @DisplayName("uses vector candidates and ranks the most similar post first")
    void shouldGeneratePersonalizedFeed() {
        when(userEmbeddingRepository.findById(USER_ID)).thenReturn(Optional.of(user(new float[]{1, 0})));
        when(postEmbeddingRepository.findPersonalizedCandidates(eq(USER_ID), anyString(), anyInt()))
                .thenReturn(List.of(candidate(POST_A, 0.95, Instant.now().minus(Duration.ofDays(2))),
                        candidate(POST_B, 0.20, Instant.now().minus(Duration.ofDays(2)))));
        when(interactionRepository.countByPostIdAndType(any())).thenReturn(List.of());

        FeedResponseDto feed = useCase.execute(USER_ID, 20, 0);

        verify(postEmbeddingRepository).findPersonalizedCandidates(eq(USER_ID), anyString(), eq(500));
        verify(postEmbeddingRepository, never()).findColdStartCandidates(any(), anyInt());
        assertThat(feed.items()).extracting(item -> item.postId()).containsExactly(POST_A, POST_B);
        assertThat(feed.hasNext()).isFalse();
    }

    @Test
    @DisplayName("uses popularity and recency fallback for a user with a zero vector")
    void shouldGenerateColdStartFeed() {
        when(userEmbeddingRepository.findById(USER_ID)).thenReturn(Optional.of(user(new float[]{0, 0})));
        when(postEmbeddingRepository.findColdStartCandidates(USER_ID, 500))
                .thenReturn(List.of(candidate(POST_A, 0, Instant.now().minus(Duration.ofDays(1))),
                        candidate(POST_B, 0, Instant.now().minus(Duration.ofDays(7)))));
        when(interactionRepository.countByPostIdAndType(any()))
                .thenReturn(List.of(popularity(POST_B, InteractionType.LIKE, 100)));

        FeedResponseDto feed = useCase.execute(USER_ID, 1, 0);

        verify(postEmbeddingRepository).findColdStartCandidates(USER_ID, 500);
        assertThat(feed.items()).hasSize(1);
        assertThat(feed.hasNext()).isTrue();
    }

    @Test
    @DisplayName("paginates the already ranked candidate list")
    void shouldPaginateRankedCandidates() {
        when(userEmbeddingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(postEmbeddingRepository.findColdStartCandidates(USER_ID, 500))
                .thenReturn(List.of(candidate(POST_A, 0, Instant.now()), candidate(POST_B, 0, Instant.now().minusSeconds(1))));
        when(interactionRepository.countByPostIdAndType(any())).thenReturn(List.of());

        FeedResponseDto feed = useCase.execute(USER_ID, 1, 1);

        assertThat(feed.items()).extracting(item -> item.postId()).containsExactly(POST_B);
        assertThat(feed.hasNext()).isFalse();
    }

    private static UserEmbeddingEntity user(float[] vector) {
        return UserEmbeddingEntity.builder().userId(USER_ID).embedding(vector).updatedInstant(Instant.now()).build();
    }

    private static FeedCandidate candidate(UUID postId, double similarity, Instant creationInstant) {
        return new FeedCandidate() {
            @Override public UUID getPostId() { return postId; }
            @Override public Instant getCreationInstant() { return creationInstant; }
            @Override public double getSimilarity() { return similarity; }
        };
    }

    private static PostPopularity popularity(UUID postId, InteractionType type, long count) {
        return new PostPopularity() {
            @Override public UUID getPostId() { return postId; }
            @Override public InteractionType getType() { return type; }
            @Override public long getInteractionCount() { return count; }
        };
    }
}
