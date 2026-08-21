package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.PostEmbeddingEntity;

import java.util.UUID;

@Repository
public interface PostEmbeddingRepository extends JpaRepository<PostEmbeddingEntity, UUID> {
}
