package org.vectory.recommendationmanager.application.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.vectory.recommendationmanager.domain.enums.InteractionType;
import org.vectory.recommendationmanager.domain.util.VectorUtils;
import org.vectory.recommendationmanager.infrastructure.config.FeedProperties;
import org.vectory.recommendationmanager.infrastructure.config.FeedRankingWeights;
import org.vectory.recommendationmanager.infrastructure.config.RecommendationProperties;
import org.vectory.recommendationmanager.infrastructure.inbound.rest.dto.FeedItemResponseDto;
import org.vectory.recommendationmanager.infrastructure.inbound.rest.dto.FeedResponseDto;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.RankedFeedPost;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateFeedUseCase {

    private final UserEmbeddingRepository userEmbeddingRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final RecommendationProperties recommendationProperties;

    @Transactional(readOnly = true)
    public FeedResponseDto execute(UUID userId, int requestedPageSize, int requestedOffset) {
        FeedProperties feedProperties = recommendationProperties.feed();
        validatePagination(requestedPageSize, requestedOffset, feedProperties);

        UserEmbeddingEntity userEmbeddingEntity = userEmbeddingRepository.findById(userId).orElse(null);
        boolean hasPersonalizedEmbedding = userEmbeddingEntity != null
                && !VectorUtils.isZeroVector(userEmbeddingEntity.getEmbedding());
        String userEmbeddingVectorLiteral = null;
        if (hasPersonalizedEmbedding) {
            float[] userEmbedding = userEmbeddingEntity.getEmbedding();
            userEmbeddingVectorLiteral = VectorUtils.toPgVectorLiteral(userEmbedding);
        }
        int requestedPostCount = requestedPageSize + 1;
        Instant feedGenerationInstant = Instant.now();

        List<RankedFeedPost> rankedFeedPosts = findRankedFeedPosts(
                userId, userEmbeddingVectorLiteral, hasPersonalizedEmbedding, requestedPostCount,
                requestedOffset, feedGenerationInstant, feedProperties);
        boolean hasNextPage = rankedFeedPosts.size() > requestedPageSize;
        List<FeedItemResponseDto> feedItems = rankedFeedPosts.stream()
                .limit(requestedPageSize)
                .map(rankedFeedPost -> new FeedItemResponseDto(
                        rankedFeedPost.getPostId(), rankedFeedPost.getRankingScore()))
                .toList();

        return new FeedResponseDto(userId, feedItems, requestedPageSize, requestedOffset,
                hasNextPage, feedGenerationInstant);
    }

    private List<RankedFeedPost> findRankedFeedPosts(UUID userId, String userEmbeddingVectorLiteral,
                                                      boolean hasPersonalizedEmbedding, int requestedPostCount,
                                                      int requestedOffset, Instant feedGenerationInstant,
                                                      FeedProperties feedProperties) {
        FeedRankingWeights rankingWeights = feedProperties.weights();
        Map<InteractionType, Double> interactionWeights = recommendationProperties.interaction().weights();
        double viewInteractionWeight = interactionWeights.getOrDefault(InteractionType.VIEW, 0.0);
        double likeInteractionWeight = interactionWeights.getOrDefault(InteractionType.LIKE, 0.0);
        double saveInteractionWeight = interactionWeights.getOrDefault(InteractionType.SAVE, 0.0);
        double shareInteractionWeight = interactionWeights.getOrDefault(InteractionType.SHARE, 0.0);

        return postEmbeddingRepository.findRankedFeedPosts(
                userId,
                userEmbeddingVectorLiteral,
                hasPersonalizedEmbedding,
                feedProperties.candidateLimit(),
                requestedPostCount,
                requestedOffset,
                feedGenerationInstant,
                feedProperties.recencyHalfLife().toSeconds(),
                rankingWeights.semantic(),
                rankingWeights.recency(),
                rankingWeights.popularity(),
                viewInteractionWeight,
                likeInteractionWeight,
                saveInteractionWeight,
                shareInteractionWeight
        );
    }

    private void validatePagination(int requestedPageSize, int requestedOffset, FeedProperties feedProperties) {
        if (requestedPageSize < 1 || requestedPageSize > feedProperties.maxLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and %d".formatted(feedProperties.maxLimit()));
        }
        if (requestedOffset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset must not be negative");
        }
    }
}
