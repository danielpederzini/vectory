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
            SELECT p.post_id AS "postId", p.creation_instant AS "creationInstant",
                   1 - (p.embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM post_embeddings p
            WHERE NOT EXISTS (
                SELECT 1 FROM interactions i
                WHERE i.user_id = :userId AND i.post_id = p.post_id
            )
            ORDER BY p.embedding <=> CAST(:embedding AS vector), p.post_id
            LIMIT :limit
            """, nativeQuery = true)
    java.util.List<FeedCandidate> findPersonalizedCandidates(
            @Param("userId") UUID userId,
            @Param("embedding") String embedding,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT p.post_id AS "postId", p.creation_instant AS "creationInstant", 0.0 AS similarity
            FROM post_embeddings p
            WHERE NOT EXISTS (
                SELECT 1 FROM interactions i
                WHERE i.user_id = :userId AND i.post_id = p.post_id
            )
            ORDER BY p.creation_instant DESC, p.post_id
            LIMIT :limit
            """, nativeQuery = true)
    java.util.List<FeedCandidate> findColdStartCandidates(
            @Param("userId") UUID userId,
            @Param("limit") int limit
    );
}
