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
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.RankedFeedPost;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenerateFeedUseCase")
class GenerateFeedUseCaseTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID FIRST_POST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private UserEmbeddingRepository userEmbeddingRepository;
    @Mock private PostEmbeddingRepository postEmbeddingRepository;

    private GenerateFeedUseCase generateFeedUseCase;

    @BeforeEach
    void setUp() {
        RecommendationProperties recommendationProperties = new RecommendationProperties(
                new InteractionProperties(Map.of(InteractionType.LIKE, 1.0)),
                new UserEmbeddingProperties(0.2),
                new CronProperties(Duration.ofMinutes(5), 500),
                new FeedProperties(500, 20, 50, Duration.ofDays(7), new FeedRankingWeights(0.65, 0.25, 0.10)));
        generateFeedUseCase = new GenerateFeedUseCase(
                userEmbeddingRepository, postEmbeddingRepository, recommendationProperties);
    }

    @Test
    @DisplayName("uses a personalized vector query when the user has a non-zero embedding")
    void shouldUsePersonalizedFeedQuery() {
        UserEmbeddingEntity userEmbeddingEntity = userEmbeddingEntity(new float[]{1, 0});
        when(userEmbeddingRepository.findById(USER_ID)).thenReturn(Optional.of(userEmbeddingEntity));
        stubRankedFeedPosts(List.of(rankedFeedPost(FIRST_POST_ID, 0.9)));

        FeedResponseDto feedResponse = generateFeedUseCase.execute(USER_ID, 20, 0);

        verify(postEmbeddingRepository).findRankedFeedPosts(
                eq(USER_ID), anyString(), eq(true), eq(500), eq(21), eq(0), any(Instant.class), eq(604800L),
                eq(0.65), eq(0.25), eq(0.10), eq(0.0), eq(1.0), eq(0.0), eq(0.0));
        assertThat(feedResponse.items()).extracting(item -> item.postId()).containsExactly(FIRST_POST_ID);
    }

    @Test
    @DisplayName("uses a cold-start query mode when no user embedding exists")
    void shouldUseColdStartFeedQuery() {
        when(userEmbeddingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        stubRankedFeedPosts(List.of(rankedFeedPost(FIRST_POST_ID, 0.9)));

        generateFeedUseCase.execute(USER_ID, 20, 0);

        verify(postEmbeddingRepository).findRankedFeedPosts(
                eq(USER_ID), any(), eq(false), eq(500), eq(21), eq(0), any(Instant.class), eq(604800L),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("uses one additional result to identify whether another page exists")
    void shouldIdentifyNextPageFromAdditionalRankedPost() {
        when(userEmbeddingRepository.findById(USER_ID)).thenReturn(Optional.empty());
        RankedFeedPost additionalRankedFeedPost = mock(RankedFeedPost.class);
        stubRankedFeedPosts(List.of(
                rankedFeedPost(FIRST_POST_ID, 0.9), additionalRankedFeedPost));

        FeedResponseDto feedResponse = generateFeedUseCase.execute(USER_ID, 1, 0);

        assertThat(feedResponse.items()).extracting(item -> item.postId()).containsExactly(FIRST_POST_ID);
        assertThat(feedResponse.hasNext()).isTrue();
    }

    private void stubRankedFeedPosts(List<RankedFeedPost> rankedFeedPosts) {
        when(postEmbeddingRepository.findRankedFeedPosts(
                any(), any(), anyBoolean(), anyInt(), anyInt(), anyInt(), any(), anyLong(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(rankedFeedPosts);
    }

    private static UserEmbeddingEntity userEmbeddingEntity(float[] userEmbedding) {
        return UserEmbeddingEntity.builder()
                .userId(USER_ID)
                .embedding(userEmbedding)
                .updatedInstant(Instant.now())
                .build();
    }

    private static RankedFeedPost rankedFeedPost(UUID postId, double rankingScore) {
        RankedFeedPost rankedFeedPost = mock(RankedFeedPost.class);
        when(rankedFeedPost.getPostId()).thenReturn(postId);
        when(rankedFeedPost.getRankingScore()).thenReturn(rankingScore);
        return rankedFeedPost;
    }
}
