package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.PostEmbeddingEntity;

import java.util.UUID;

@Repository
public interface PostEmbeddingRepository extends JpaRepository<PostEmbeddingEntity, UUID> {

    @Query(value = """
            WITH eligible_post_candidates AS (
                SELECT post_embedding.post_id,
                       post_embedding.creation_instant,
                       CASE WHEN :hasPersonalizedEmbedding
                            THEN 1 - (post_embedding.embedding <=> CAST(:userEmbedding AS vector))
                            ELSE 0.0
                       END AS semantic_similarity
                FROM post_embeddings post_embedding
                WHERE NOT EXISTS (
                    SELECT 1 FROM interactions interaction
                    WHERE interaction.user_id = :userId
                      AND interaction.post_id = post_embedding.post_id
                )
                ORDER BY CASE WHEN :hasPersonalizedEmbedding
                              THEN post_embedding.embedding <=> CAST(:userEmbedding AS vector)
                         END ASC NULLS LAST,
                         CASE WHEN NOT :hasPersonalizedEmbedding
                              THEN post_embedding.creation_instant
                         END DESC NULLS LAST,
                         post_embedding.post_id
                LIMIT :candidateLimit
            ),
            candidate_popularity AS (
                SELECT eligible_post_candidate.post_id,
                       eligible_post_candidate.creation_instant,
                       eligible_post_candidate.semantic_similarity,
                       COALESCE(SUM(CASE interaction.type
                           WHEN 'VIEW' THEN :viewInteractionWeight
                           WHEN 'LIKE' THEN :likeInteractionWeight
                           WHEN 'SAVE' THEN :saveInteractionWeight
                           WHEN 'SHARE' THEN :shareInteractionWeight
                           ELSE 0.0
                       END), 0.0) AS weighted_popularity
                FROM eligible_post_candidates eligible_post_candidate
                LEFT JOIN interactions interaction ON interaction.post_id = eligible_post_candidate.post_id
                GROUP BY eligible_post_candidate.post_id,
                         eligible_post_candidate.creation_instant,
                         eligible_post_candidate.semantic_similarity
            ),
            normalized_candidate_scores AS (
                SELECT candidate_popularity.*,
                       EXP(-LN(2) * GREATEST(0, EXTRACT(EPOCH FROM
                           (CAST(:currentInstant AS timestamptz) - creation_instant)))
                           / :recencyHalfLifeSeconds) AS recency_score,
                       MAX(weighted_popularity) OVER () AS maximum_weighted_popularity
                FROM candidate_popularity
            )
            SELECT post_id AS "postId",
                   (
                       CASE WHEN :hasPersonalizedEmbedding THEN
                           :semanticRankingWeight * GREATEST(0.0, LEAST(1.0, (semantic_similarity + 1.0) / 2.0))
                           + :recencyRankingWeight * recency_score
                           + :popularityRankingWeight * CASE WHEN maximum_weighted_popularity = 0.0 THEN 0.0
                               ELSE LN(1.0 + weighted_popularity) / LN(1.0 + maximum_weighted_popularity)
                           END
                       ELSE
                           :recencyRankingWeight * recency_score
                           + :popularityRankingWeight * CASE WHEN maximum_weighted_popularity = 0.0 THEN 0.0
                               ELSE LN(1.0 + weighted_popularity) / LN(1.0 + maximum_weighted_popularity)
                           END
                       END
                       / CASE WHEN :hasPersonalizedEmbedding
                           THEN :semanticRankingWeight + :recencyRankingWeight + :popularityRankingWeight
                           ELSE :recencyRankingWeight + :popularityRankingWeight
                       END
                   ) AS "rankingScore"
            FROM normalized_candidate_scores
            ORDER BY "rankingScore" DESC, post_id
            LIMIT :requestedPostCount OFFSET :offset
            """, nativeQuery = true)
    java.util.List<RankedFeedPost> findRankedFeedPosts(
            @Param("userId") UUID userId,
            @Param("userEmbedding") String userEmbedding,
            @Param("hasPersonalizedEmbedding") boolean hasPersonalizedEmbedding,
            @Param("candidateLimit") int candidateLimit,
            @Param("requestedPostCount") int requestedPostCount,
            @Param("offset") int offset,
            @Param("currentInstant") java.time.Instant currentInstant,
            @Param("recencyHalfLifeSeconds") long recencyHalfLifeSeconds,
            @Param("semanticRankingWeight") double semanticRankingWeight,
            @Param("recencyRankingWeight") double recencyRankingWeight,
            @Param("popularityRankingWeight") double popularityRankingWeight,
            @Param("viewInteractionWeight") double viewInteractionWeight,
            @Param("likeInteractionWeight") double likeInteractionWeight,
            @Param("saveInteractionWeight") double saveInteractionWeight,
            @Param("shareInteractionWeight") double shareInteractionWeight
    );
}
