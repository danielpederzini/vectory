package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import org.vectory.recommendationmanager.infrastructure.outbound.persistence.entity.InteractionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface InteractionRepository extends JpaRepository<InteractionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<InteractionEntity> findByProcessedInstantIsNullOrderByCreationInstantAsc(Limit limit);
}
