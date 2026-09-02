package org.vectory.recommendationmanager.application.usecase;

import java.util.UUID;

public record RankedPostCandidate(UUID postId, double rankingScore) {
}
