package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.InteractionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface InteractionRepository extends JpaRepository<InteractionEntity, UUID> {

    @Query(value = """
            SELECT * FROM interactions
            WHERE processed_instant IS NULL
            ORDER BY creation_instant ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InteractionEntity> claimUnprocessed(@Param("limit") int limit);

    @Query("""
            select i.postId as postId, i.type as type, count(i) as interactionCount
            from InteractionEntity i
            where i.postId in :postIds
            group by i.postId, i.type
            """)
    List<PostPopularity> countByPostIdAndType(@Param("postIds") List<UUID> postIds);
}
