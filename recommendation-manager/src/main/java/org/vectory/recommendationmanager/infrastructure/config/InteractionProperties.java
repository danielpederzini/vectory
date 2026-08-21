package org.vectory.recommendationmanager.infrastructure.config;

import org.vectory.recommendationmanager.domain.enums.InteractionType;

import java.util.Map;

public record InteractionProperties(Map<InteractionType, Double> weights) {
}
