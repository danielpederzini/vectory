package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import org.vectory.recommendationmanager.domain.enums.InteractionType;

import java.util.UUID;

public interface PostPopularity {

    UUID getPostId();

    InteractionType getType();

    long getInteractionCount();
}
