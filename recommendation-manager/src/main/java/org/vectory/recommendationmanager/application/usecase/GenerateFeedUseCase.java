package org.vectory.recommendationmanager.application.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
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
        FeedProperties feed = properties.feed();
        validatePagination(limit, offset, feed);

        UserEmbeddingEntity user = userEmbeddingRepository.findById(userId).orElse(null);
        boolean personalized = user != null && hasUsableEmbedding(user.getEmbedding());
        List<FeedCandidate> candidates = personalized
                ? postEmbeddingRepository.findPersonalizedCandidates(userId, vectorLiteral(user.getEmbedding()), feed.candidateLimit())
                : postEmbeddingRepository.findColdStartCandidates(userId, feed.candidateLimit());

        List<ScoredCandidate> ranked = rank(candidates, personalized, Instant.now());
        List<FeedItemResponseDto> items = ranked.stream()
                .skip(offset)
                .limit(limit)
                .map(candidate -> new FeedItemResponseDto(candidate.postId(), candidate.score()))
                .toList();

        return new FeedResponseDto(userId, items, limit, offset, offset + items.size() < ranked.size(), Instant.now());
    }

    private List<ScoredCandidate> rank(List<FeedCandidate> candidates, boolean personalized, Instant now) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<UUID, Double> popularity = popularityByPost(candidates);
        double maxPopularity = popularity.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        FeedProperties feed = properties.feed();

        return candidates.stream()
                .map(candidate -> score(candidate, popularity.getOrDefault(candidate.getPostId(), 0.0),
                        maxPopularity, personalized, feed, now))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.postId().toString()))
                .toList();
    }

    private Map<UUID, Double> popularityByPost(List<FeedCandidate> candidates) {
        List<UUID> postIds = candidates.stream().map(FeedCandidate::getPostId).toList();
        Map<UUID, Double> popularity = new HashMap<>();
        for (PostPopularity count : interactionRepository.countByPostIdAndType(postIds)) {
            double weight = properties.interaction().weights().getOrDefault(count.getType(), 0.0);
            popularity.merge(count.getPostId(), weight * count.getInteractionCount(), Double::sum);
        }
        return popularity;
    }

    private ScoredCandidate score(FeedCandidate candidate, double popularity, double maxPopularity,
                                  boolean personalized, FeedProperties feed, Instant now) {
        FeedRankingWeights weights = feed.weights();
        double semantic = personalized ? clamp((candidate.getSimilarity() + 1.0) / 2.0) : 0.0;
        double recency = recencyScore(candidate.getCreationInstant(), now, feed.recencyHalfLife());
        double popularityScore = maxPopularity == 0.0 ? 0.0 : Math.log1p(popularity) / Math.log1p(maxPopularity);
        double weightTotal = personalized
                ? weights.semantic() + weights.recency() + weights.popularity()
                : weights.recency() + weights.popularity();
        double rawScore = personalized
                ? weights.semantic() * semantic + weights.recency() * recency + weights.popularity() * popularityScore
                : weights.recency() * recency + weights.popularity() * popularityScore;
        return new ScoredCandidate(candidate.getPostId(), weightTotal == 0.0 ? 0.0 : rawScore / weightTotal);
    }

    private double recencyScore(Instant creationInstant, Instant now, Duration halfLife) {
        double ageSeconds = Math.max(0, Duration.between(creationInstant, now).toSeconds());
        return Math.exp(-Math.log(2) * ageSeconds / halfLife.toSeconds());
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

    private record ScoredCandidate(UUID postId, double score) {
    }
}
