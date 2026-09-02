package org.vectory.recommendationmanager.application.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.vectory.recommendationmanager.infrastructure.config.FeedProperties;
import org.vectory.recommendationmanager.infrastructure.config.FeedRankingWeights;
import org.vectory.recommendationmanager.infrastructure.config.RecommendationProperties;
import org.vectory.recommendationmanager.infrastructure.inbound.rest.dto.FeedItemResponseDto;
import org.vectory.recommendationmanager.infrastructure.inbound.rest.dto.FeedResponseDto;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.FeedCandidate;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.InteractionRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostPopularity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class GenerateFeedUseCase {

    private final UserEmbeddingRepository userEmbeddingRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final InteractionRepository interactionRepository;
    private final RecommendationProperties properties;

    @Transactional(readOnly = true)
    public FeedResponseDto execute(UUID userId, int limit, int offset) {
        FeedProperties feedProperties = properties.feed();
        validatePagination(limit, offset, feedProperties);

        UserEmbeddingEntity userEmbeddingEntity = userEmbeddingRepository.findById(userId).orElse(null);
        boolean hasPersonalizedEmbedding = userEmbeddingEntity != null
                && hasUsableEmbedding(userEmbeddingEntity.getEmbedding());
        List<FeedCandidate> feedCandidates = hasPersonalizedEmbedding
                ? postEmbeddingRepository.findPersonalizedCandidates(
                        userId, vectorLiteral(userEmbeddingEntity.getEmbedding()), feedProperties.candidateLimit())
                : postEmbeddingRepository.findColdStartCandidates(userId, feedProperties.candidateLimit());

        List<RankedPostCandidate> rankedPostCandidates = rankFeedCandidates(
                feedCandidates, hasPersonalizedEmbedding, Instant.now());
        List<FeedItemResponseDto> feedItems = rankedPostCandidates.stream()
                .skip(offset)
                .limit(limit)
                .map(rankedPostCandidate -> new FeedItemResponseDto(
                        rankedPostCandidate.postId(), rankedPostCandidate.rankingScore()))
                .toList();

        return new FeedResponseDto(userId, feedItems, limit, offset,
                offset + feedItems.size() < rankedPostCandidates.size(), Instant.now());
    }

    private List<RankedPostCandidate> rankFeedCandidates(List<FeedCandidate> feedCandidates,
                                                           boolean hasPersonalizedEmbedding,
                                                           Instant currentInstant) {
        if (feedCandidates.isEmpty()) {
            return List.of();
        }

        Map<UUID, Double> popularityByPost = calculatePopularityByPost(feedCandidates);
        double maximumPopularity = popularityByPost.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0.0);
        FeedProperties feedProperties = properties.feed();

        return feedCandidates.stream()
                .map(feedCandidate -> calculateRankingScore(feedCandidate,
                        popularityByPost.getOrDefault(feedCandidate.getPostId(), 0.0), maximumPopularity,
                        hasPersonalizedEmbedding, feedProperties, currentInstant))
                .sorted(Comparator.comparingDouble(RankedPostCandidate::rankingScore).reversed()
                        .thenComparing(rankedPostCandidate -> rankedPostCandidate.postId().toString()))
                .toList();
    }

    private Map<UUID, Double> calculatePopularityByPost(List<FeedCandidate> feedCandidates) {
        List<UUID> candidatePostIds = feedCandidates.stream().map(FeedCandidate::getPostId).toList();
        Map<UUID, Double> popularityByPost = new HashMap<>();
        for (PostPopularity postPopularity : interactionRepository.countByPostIdAndType(candidatePostIds)) {
            double interactionWeight = properties.interaction().weights()
                    .getOrDefault(postPopularity.getType(), 0.0);
            popularityByPost.merge(postPopularity.getPostId(),
                    interactionWeight * postPopularity.getInteractionCount(), Double::sum);
        }
        return popularityByPost;
    }

    private RankedPostCandidate calculateRankingScore(FeedCandidate feedCandidate, double postPopularity,
                                                       double maximumPopularity, boolean hasPersonalizedEmbedding,
                                                       FeedProperties feedProperties, Instant currentInstant) {
        FeedRankingWeights rankingWeights = feedProperties.weights();
        double semanticSimilarityScore = hasPersonalizedEmbedding
                ? clamp((feedCandidate.getSimilarity() + 1.0) / 2.0) : 0.0;
        double recencyScore = calculateRecencyScore(
                feedCandidate.getCreationInstant(), currentInstant, feedProperties.recencyHalfLife());
        double popularityScore = maximumPopularity == 0.0
                ? 0.0 : Math.log1p(postPopularity) / Math.log1p(maximumPopularity);
        double activeRankingWeightTotal = hasPersonalizedEmbedding
                ? rankingWeights.semantic() + rankingWeights.recency() + rankingWeights.popularity()
                : rankingWeights.recency() + rankingWeights.popularity();
        double weightedRankingScore = hasPersonalizedEmbedding
                ? rankingWeights.semantic() * semanticSimilarityScore + rankingWeights.recency() * recencyScore
                + rankingWeights.popularity() * popularityScore
                : rankingWeights.recency() * recencyScore + rankingWeights.popularity() * popularityScore;
        return new RankedPostCandidate(feedCandidate.getPostId(), activeRankingWeightTotal == 0.0
                ? 0.0 : weightedRankingScore / activeRankingWeightTotal);
    }

    private double calculateRecencyScore(Instant creationInstant, Instant currentInstant, Duration recencyHalfLife) {
        double postAgeSeconds = Math.max(0, Duration.between(creationInstant, currentInstant).toSeconds());
        return Math.exp(-Math.log(2) * postAgeSeconds / recencyHalfLife.toSeconds());
    }

    private boolean hasUsableEmbedding(float[] embedding) {
        for (float value : embedding) {
            if (value != 0.0f) {
                return true;
            }
        }
        return false;
    }

    private String vectorLiteral(float[] embedding) {
        StringJoiner values = new StringJoiner(",", "[", "]");
        for (float value : embedding) {
            values.add(Float.toString(value));
        }
        return values.toString();
    }

    private void validatePagination(int limit, int offset, FeedProperties feed) {
        if (limit < 1 || limit > feed.maxLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and %d".formatted(feed.maxLimit()));
        }
        if (offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset must not be negative");
        }
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

}
