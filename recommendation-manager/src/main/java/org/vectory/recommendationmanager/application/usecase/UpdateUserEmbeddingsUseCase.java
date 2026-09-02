package org.vectory.recommendationmanager.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vectory.recommendationmanager.domain.enums.InteractionType;
import org.vectory.recommendationmanager.domain.util.VectorUtils;
import org.vectory.recommendationmanager.infrastructure.config.RecommendationProperties;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.InteractionEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.PostEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.InteractionRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.PostEmbeddingRepository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository.UserEmbeddingRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateUserEmbeddingsUseCase implements RunnableUseCase {

    private final InteractionRepository interactionRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final UserEmbeddingRepository userEmbeddingRepository;
    private final RecommendationProperties properties;

    @Override
    @Scheduled(fixedDelayString = "${recommendation-manager.cron.fixed-delay:PT5M}")
    @Transactional
    public void execute() {
        List<InteractionEntity> pending = interactionRepository
                .claimUnprocessed(properties.cron().batchSize());
        if (pending.isEmpty()) {
            return;
        }

        Map<UUID, float[]> postVectors = loadPostVectors(pending);
        Map<UUID, UserEmbeddingEntity> userEmbeddings = loadUserEmbeddings(pending);
        Map<UUID, List<InteractionEntity>> interactionsByUser = pending.stream()
                .collect(Collectors.groupingBy(InteractionEntity::getUserId));

        Instant now = Instant.now();
        interactionsByUser.forEach((userId, interactions) ->
                updateForUser(userEmbeddings.get(userId), interactions, postVectors, now));
    }

    private void updateForUser(UserEmbeddingEntity userEmbedding,
                               List<InteractionEntity> interactions,
                               Map<UUID, float[]> postVectors,
                               Instant now) {
        if (userEmbedding == null) {
            return;
        }

        List<float[]> vectors = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        List<InteractionEntity> consideredInteractions = new ArrayList<>();

        for (InteractionEntity interaction : interactions) {
            float[] postVector = postVectors.get(interaction.getPostId());
            if (postVector == null) {
                continue;
            }
            consideredInteractions.add(interaction);
            double weight = weightFor(interaction.getType());
            if (weight > 0.0) {
                vectors.add(postVector);
                weights.add(weight);
            }
        }

        if (consideredInteractions.isEmpty()) {
            return;
        }

        blendEmbedding(userEmbedding, vectors, weights, now);
        markProcessed(consideredInteractions, now);
        log.debug("updated embedding for user {} from {} interactions",
                userEmbedding.getUserId(), consideredInteractions.size());
    }

    private void blendEmbedding(UserEmbeddingEntity userEmbedding,
                                List<float[]> vectors,
                                List<Double> weights,
                                Instant now) {
        if (vectors.isEmpty()) {
            return;
        }
        double alpha = properties.userEmbedding().alpha();
        float[] weightedAverage = VectorUtils.weightedAverage(vectors, weights);
        float[] blended = VectorUtils.normalize(VectorUtils.blend(userEmbedding.getEmbedding(), weightedAverage, alpha));
        userEmbedding.setEmbedding(blended);
        userEmbedding.setUpdatedInstant(now);
    }

    private void markProcessed(List<InteractionEntity> interactions, Instant now) {
        interactions.forEach(interaction -> interaction.setProcessedInstant(now));
    }

    private Map<UUID, float[]> loadPostVectors(List<InteractionEntity> interactions) {
        List<UUID> postIds = interactions.stream().map(InteractionEntity::getPostId).distinct().toList();
        Map<UUID, float[]> vectors = new HashMap<>();
        for (PostEmbeddingEntity post : postEmbeddingRepository.findAllById(postIds)) {
            vectors.put(post.getPostId(), post.getEmbedding());
        }
        return vectors;
    }

    private Map<UUID, UserEmbeddingEntity> loadUserEmbeddings(List<InteractionEntity> interactions) {
        List<UUID> userIds = interactions.stream().map(InteractionEntity::getUserId).distinct().toList();
        Map<UUID, UserEmbeddingEntity> embeddings = new HashMap<>();
        for (UserEmbeddingEntity user : userEmbeddingRepository.findAllById(userIds)) {
            embeddings.put(user.getUserId(), user);
        }
        return embeddings;
    }

    private double weightFor(InteractionType type) {
        return properties.interaction().weights().getOrDefault(type, 0.0);
    }
}
