package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.UserEmbeddingEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserEmbeddingRepository extends JpaRepository<UserEmbeddingEntity, UUID> {

    @Query("select u.embedding from UserEmbeddingEntity u")
    List<float[]> findAllEmbeddings();
}
