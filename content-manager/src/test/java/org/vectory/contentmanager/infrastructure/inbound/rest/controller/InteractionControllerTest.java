package org.vectory.contentmanager.infrastructure.inbound.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vectory.contentmanager.application.service.InteractionService;
import org.vectory.contentmanager.domain.enums.InteractionType;
import org.vectory.contentmanager.domain.exception.DuplicateInteractionException;
import org.vectory.contentmanager.domain.exception.HttpStatusException;
import org.vectory.contentmanager.domain.exception.PostNotFoundException;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.InteractionResponseDto;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InteractionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("POST /api/v1/interactions")
class InteractionControllerTest {

    private static final String INTERACTIONS_ENDPOINT = "/api/v1/interactions";

    private static final String INTERACTION_ID_VALUE = "33333333-3333-3333-3333-333333333333";
    private static final String POST_ID_VALUE = "11111111-1111-1111-1111-111111111111";
    private static final String USER_ID_VALUE = "44444444-4444-4444-4444-444444444444";
    private static final String CREATION_INSTANT_VALUE = "2026-01-15T10:15:30Z";
    private static final UUID INTERACTION_ID = UUID.fromString(INTERACTION_ID_VALUE);
    private static final UUID POST_ID = UUID.fromString(POST_ID_VALUE);
    private static final UUID USER_ID = UUID.fromString(USER_ID_VALUE);
    private static final Instant CREATION_INSTANT = Instant.parse(CREATION_INSTANT_VALUE);

    private static final InteractionType DEFAULT_TYPE = InteractionType.LIKE;
    private static final String UNKNOWN_TYPE_VALUE = "SUPERLIKE";
    private static final String CONSTRAINT_VIOLATION_MESSAGE = "uq_interactions violated";

    private static final String BODY_TEMPLATE = """
            {
              "postId": "%s",
              "userId": "%s",
              "type": "%s"
            }
            """;
    private static final String EMPTY_BODY = "{}";
    private static final String BODY_WITH_UNKNOWN_TYPE = buildBody(UNKNOWN_TYPE_VALUE);

    private static final String ID_PATH = "$.id";
    private static final String POST_ID_PATH = "$.postId";
    private static final String USER_ID_PATH = "$.userId";
    private static final String TYPE_PATH = "$.type";
    private static final String CREATION_INSTANT_PATH = "$.creationInstant";
    private static final String STATUS_PATH = "$.status";
    private static final String ERROR_FIELD_PATH_TEMPLATE = "$.errors['%s']";

    private static final String POST_ID_FIELD = "postId";
    private static final String USER_ID_FIELD = "userId";
    private static final String TYPE_FIELD = "type";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InteractionService interactionService;

    private static String buildBody(Object type) {
        return BODY_TEMPLATE.formatted(POST_ID_VALUE, USER_ID_VALUE, type);
    }

    private static InteractionResponseDto buildResponse(InteractionType type) {
        return InteractionResponseDto.builder()
                .id(INTERACTION_ID)
                .postId(POST_ID)
                .userId(USER_ID)
                .type(type)
                .creationInstant(CREATION_INSTANT)
                .build();
    }

    private static Stream<Arguments> provideFailuresWithExpectedStatus() {
        return Stream.of(
                Arguments.of(new PostNotFoundException(POST_ID), HttpStatus.NOT_FOUND),
                Arguments.of(
                        new DuplicateInteractionException(
                                POST_ID,
                                USER_ID,
                                DEFAULT_TYPE,
                                new DataIntegrityViolationException(CONSTRAINT_VIOLATION_MESSAGE)),
                        HttpStatus.CONFLICT)
        );
    }

    @Test
    @DisplayName("returns 201 with the created interaction")
    void shouldReturnCreatedWithTheCreatedInteraction() throws Exception {
        when(interactionService.create(any(InteractionCreationRequestDto.class)))
                .thenReturn(buildResponse(DEFAULT_TYPE));

        mockMvc.perform(post(INTERACTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBody(DEFAULT_TYPE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(ID_PATH).value(INTERACTION_ID_VALUE))
                .andExpect(jsonPath(POST_ID_PATH).value(POST_ID_VALUE))
                .andExpect(jsonPath(USER_ID_PATH).value(USER_ID_VALUE))
                .andExpect(jsonPath(TYPE_PATH).value(DEFAULT_TYPE.name()))
                .andExpect(jsonPath(CREATION_INSTANT_PATH).value(CREATION_INSTANT_VALUE));
    }

    @ParameterizedTest(name = "type {0}")
    @EnumSource(InteractionType.class)
    @DisplayName("returns 201 for every supported interaction type")
    void shouldAcceptEverySupportedInteractionType(InteractionType type) throws Exception {
        when(interactionService.create(any(InteractionCreationRequestDto.class))).thenReturn(buildResponse(type));

        mockMvc.perform(post(INTERACTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBody(type)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(TYPE_PATH).value(type.name()));
    }

    @ParameterizedTest(name = "rejected field: {0}")
    @ValueSource(strings = {POST_ID_FIELD, USER_ID_FIELD, TYPE_FIELD})
    @DisplayName("returns 400 reporting each missing required field")
    void shouldRejectRequestReportingEachMissingRequiredField(String rejectedField) throws Exception {
        mockMvc.perform(post(INTERACTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_FIELD_PATH_TEMPLATE.formatted(rejectedField)).exists());

        verify(interactionService, never()).create(any(InteractionCreationRequestDto.class));
    }

    @Test
    @DisplayName("returns 400 when the interaction type is not recognised")
    void shouldRejectUnrecognisedInteractionType() throws Exception {
        mockMvc.perform(post(INTERACTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_WITH_UNKNOWN_TYPE))
                .andExpect(status().isBadRequest());

        verify(interactionService, never()).create(any(InteractionCreationRequestDto.class));
    }

    @ParameterizedTest(name = "failure {index} maps to {1}")
    @MethodSource("provideFailuresWithExpectedStatus")
    @DisplayName("maps a domain failure onto its HTTP status")
    void shouldMapDomainFailureOntoItsHttpStatus(HttpStatusException failure, HttpStatus expectedStatus)
            throws Exception {
        when(interactionService.create(any(InteractionCreationRequestDto.class))).thenThrow(failure);

        mockMvc.perform(post(INTERACTIONS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildBody(DEFAULT_TYPE)))
                .andExpect(status().is(expectedStatus.value()))
                .andExpect(jsonPath(STATUS_PATH).value(expectedStatus.value()));
    }
}
