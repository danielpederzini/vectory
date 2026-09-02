package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import java.time.Instant;
import java.util.UUID;

public interface FeedCandidate {

    UUID getPostId();

    Instant getCreationInstant();

    double getSimilarity();
}
