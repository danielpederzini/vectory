package org.vectory.recommendationmanager.infrastructure.inbound.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vectory.recommendationmanager.application.usecase.GenerateFeedUseCase;
import org.vectory.recommendationmanager.infrastructure.config.FeedProperties;
import org.vectory.recommendationmanager.infrastructure.config.FeedRankingWeights;
import org.vectory.recommendationmanager.infrastructure.config.RecommendationProperties;
import org.vectory.recommendationmanager.infrastructure.inbound.rest.dto.FeedResponseDto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/v1/recommendations/{userId}")
class RecommendationControllerTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GenerateFeedUseCase generateFeedUseCase;
    @MockitoBean private RecommendationProperties properties;

    @Test
    @DisplayName("returns the feed using the configured default page size")
    void shouldReturnFeed() throws Exception {
        when(properties.feed()).thenReturn(new FeedProperties(500, 20, 50, Duration.ofDays(7),
                new FeedRankingWeights(0.65, 0.25, 0.10)));
        when(generateFeedUseCase.execute(eq(USER_ID), eq(20), eq(0)))
                .thenReturn(new FeedResponseDto(USER_ID, List.of(), 20, 0, false, Instant.parse("2026-01-15T10:15:30Z")));

        mockMvc.perform(get("/api/v1/recommendations/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.items").isArray());
    }
}
