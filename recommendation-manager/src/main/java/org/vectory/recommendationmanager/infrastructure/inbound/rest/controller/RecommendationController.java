package org.vectory.recommendationmanager.infrastructure.inbound.rest.controller;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.vectory.recommendationmanager.application.usecase.GenerateFeedUseCase;
import org.vectory.recommendationmanager.infrastructure.inbound.rest.dto.FeedResponseDto;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final GenerateFeedUseCase generateFeedUseCase;

    @GetMapping("/{userId}")
    public ResponseEntity<FeedResponseDto> getFeed(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        return ResponseEntity.ok(generateFeedUseCase.execute(userId, limit, offset));
    }
}
