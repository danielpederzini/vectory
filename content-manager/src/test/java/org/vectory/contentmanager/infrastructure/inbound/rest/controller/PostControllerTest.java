package org.vectory.contentmanager.infrastructure.inbound.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vectory.contentmanager.application.service.PostService;
import org.vectory.contentmanager.domain.enums.PostMediaType;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostCreationRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostMediaResponseDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.PostResponseDto;

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

@WebMvcTest(controllers = PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("POST /api/v1/posts")
class PostControllerTest {

    private static final String POSTS_ENDPOINT = "/api/v1/posts";

    private static final String POST_ID_VALUE = "11111111-1111-1111-1111-111111111111";
    private static final String AUTHOR_ID_VALUE = "22222222-2222-2222-2222-222222222222";
    private static final String CREATION_INSTANT_VALUE = "2026-01-15T10:15:30Z";
    private static final UUID POST_ID = UUID.fromString(POST_ID_VALUE);
    private static final UUID AUTHOR_ID = UUID.fromString(AUTHOR_ID_VALUE);
    private static final Instant CREATION_INSTANT = Instant.parse(CREATION_INSTANT_VALUE);

    private static final String POST_TEXT = "hello world";
    private static final String MEDIA_URL = "https://cdn.vectory.org/media/cat.png";
    private static final PostMediaType MEDIA_TYPE = PostMediaType.IMAGE;
    private static final int MAX_TEXT_LENGTH = 5000;

    private static final String BODY_WITH_MEDIA = """
            {
              "authorId": "%s",
              "text": "%s",
              "media": {
                "mediaType": "%s",
                "mediaUrl": "%s"
              }
            }
            """.formatted(AUTHOR_ID_VALUE, POST_TEXT, MEDIA_TYPE, MEDIA_URL);

    private static final String BODY_WITHOUT_MEDIA = """
            {
              "authorId": "%s",
              "text": "%s"
            }
            """.formatted(AUTHOR_ID_VALUE, POST_TEXT);

    private static final String BODY_WITHOUT_AUTHOR_ID = """
            {
              "text": "%s"
            }
            """.formatted(POST_TEXT);

    private static final String BODY_WITH_OVERLONG_TEXT = """
            {
              "authorId": "%s",
              "text": "%s"
            }
            """.formatted(AUTHOR_ID_VALUE, "a".repeat(MAX_TEXT_LENGTH + 1));

    private static final String BODY_WITH_MEDIA_MISSING_URL = """
            {
              "authorId": "%s",
              "media": {
                "mediaType": "%s"
              }
            }
            """.formatted(AUTHOR_ID_VALUE, MEDIA_TYPE);

    private static final String MALFORMED_BODY = "{ not json";

    private static final String ID_PATH = "$.id";
    private static final String AUTHOR_ID_PATH = "$.authorId";
    private static final String TEXT_PATH = "$.text";
    private static final String CREATION_INSTANT_PATH = "$.creationInstant";
    private static final String MEDIA_PATH = "$.media";
    private static final String MEDIA_TYPE_PATH = "$.media.mediaType";
    private static final String MEDIA_URL_PATH = "$.media.mediaUrl";
    private static final String ERROR_FIELD_PATH_TEMPLATE = "$.errors['%s']";

    private static final String AUTHOR_ID_FIELD = "authorId";
    private static final String TEXT_FIELD = "text";
    private static final String MEDIA_URL_FIELD = "media.mediaUrl";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    private static Stream<Arguments> provideInvalidBodiesWithRejectedField() {
        return Stream.of(
                Arguments.of(BODY_WITHOUT_AUTHOR_ID, AUTHOR_ID_FIELD),
                Arguments.of(BODY_WITH_OVERLONG_TEXT, TEXT_FIELD),
                Arguments.of(BODY_WITH_MEDIA_MISSING_URL, MEDIA_URL_FIELD)
        );
    }

    private static PostResponseDto buildResponse(PostMediaResponseDto media) {
        return PostResponseDto.builder()
                .id(POST_ID)
                .authorId(AUTHOR_ID)
                .text(POST_TEXT)
                .creationInstant(CREATION_INSTANT)
                .media(media)
                .build();
    }

    @Test
    @DisplayName("returns 201 with the created post")
    void shouldReturnCreatedWithTheCreatedPost() throws Exception {
        PostMediaResponseDto media = PostMediaResponseDto.builder()
                .mediaType(MEDIA_TYPE)
                .mediaUrl(MEDIA_URL)
                .build();
        when(postService.create(any(PostCreationRequestDto.class))).thenReturn(buildResponse(media));

        mockMvc.perform(post(POSTS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_WITH_MEDIA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(ID_PATH).value(POST_ID_VALUE))
                .andExpect(jsonPath(AUTHOR_ID_PATH).value(AUTHOR_ID_VALUE))
                .andExpect(jsonPath(TEXT_PATH).value(POST_TEXT))
                .andExpect(jsonPath(CREATION_INSTANT_PATH).value(CREATION_INSTANT_VALUE))
                .andExpect(jsonPath(MEDIA_TYPE_PATH).value(MEDIA_TYPE.name()))
                .andExpect(jsonPath(MEDIA_URL_PATH).value(MEDIA_URL));
    }

    @Test
    @DisplayName("returns 201 for a post submitted without media")
    void shouldAcceptPostSubmittedWithoutMedia() throws Exception {
        when(postService.create(any(PostCreationRequestDto.class))).thenReturn(buildResponse(null));

        mockMvc.perform(post(POSTS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_WITHOUT_MEDIA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath(MEDIA_PATH).doesNotExist());
    }

    @ParameterizedTest(name = "rejected field: {1}")
    @MethodSource("provideInvalidBodiesWithRejectedField")
    @DisplayName("returns 400 reporting the rejected field when the request is invalid")
    void shouldRejectInvalidRequestReportingTheRejectedField(String body, String rejectedField) throws Exception {
        mockMvc.perform(post(POSTS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(ERROR_FIELD_PATH_TEMPLATE.formatted(rejectedField)).exists());

        verify(postService, never()).create(any(PostCreationRequestDto.class));
    }

    @Test
    @DisplayName("returns 400 when the request body is not valid JSON")
    void shouldRejectRequestWhoseBodyIsNotValidJson() throws Exception {
        mockMvc.perform(post(POSTS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MALFORMED_BODY))
                .andExpect(status().isBadRequest());

        verify(postService, never()).create(any(PostCreationRequestDto.class));
    }
}
