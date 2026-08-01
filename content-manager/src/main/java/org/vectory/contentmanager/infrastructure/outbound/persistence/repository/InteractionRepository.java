package org.vectory.contentmanager.infrastructure.outbound.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.vectory.contentmanager.infrastructure.outbound.persistence.entity.InteractionEntity;

import java.util.UUID;

@Repository
public interface InteractionRepository extends JpaRepository<InteractionEntity, UUID> {
}
