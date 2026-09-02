package org.vectory.recommendationmanager.infrastructure.outbound.persistence.repository;

import java.util.UUID;

public interface RankedFeedPost {

    UUID getPostId();

    double getRankingScore();
}
