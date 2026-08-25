package org.vectory.contentmanager.infrastructure.inbound.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.vectory.contentmanager.application.service.MediaService;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadResponseDto;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MediaController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("POST /api/v1/media/uploads")
class MediaControllerTest {

    private static final String UPLOADS_ENDPOINT = "/api/v1/media/uploads";

    private static final String OBJECT_KEY = "posts/2b0f0c8e-cat.jpg";
    private static final String UPLOAD_URL = "http://localhost:9000/vectory-media/posts/2b0f0c8e-cat.jpg?signed=1";
    private static final String CONTENT_TYPE = "image/jpeg";
    private static final String EXPIRES_AT_VALUE = "2026-01-15T10:15:30Z";

    private static final String VALID_BODY = """
            {
              "mediaType": "IMAGE",
              "contentType": "image/jpeg",
              "sizeBytes": 2048
            }
            """;

    private static final String BODY_MISSING_MEDIA_TYPE = """
            {
              "contentType": "image/jpeg",
              "sizeBytes": 2048
            }
            """;

    private static final String BODY_BLANK_CONTENT_TYPE = """
            {
              "mediaType": "IMAGE",
              "contentType": "",
              "sizeBytes": 2048
            }
            """;

    private static final String BODY_NON_POSITIVE_SIZE = """
            {
              "mediaType": "IMAGE",
              "contentType": "image/jpeg",
              "sizeBytes": 0
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaService mediaService;

    private static Stream<String> provideInvalidBodies() {
        return Stream.of(BODY_MISSING_MEDIA_TYPE, BODY_BLANK_CONTENT_TYPE, BODY_NON_POSITIVE_SIZE);
    }

    @Test
    @DisplayName("returns 201 with the presigned upload details")
    void shouldReturnCreatedWithPresignedUpload() throws Exception {
        MediaUploadResponseDto response = MediaUploadResponseDto.builder()
                .objectKey(OBJECT_KEY)
                .uploadUrl(UPLOAD_URL)
                .httpMethod("PUT")
                .requiredHeaders(Map.of("Content-Type", CONTENT_TYPE))
                .expiresAt(Instant.parse(EXPIRES_AT_VALUE))
                .build();
        when(mediaService.createUpload(any(MediaUploadRequestDto.class))).thenReturn(response);

        mockMvc.perform(post(UPLOADS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectKey").value(OBJECT_KEY))
                .andExpect(jsonPath("$.uploadUrl").value(UPLOAD_URL))
                .andExpect(jsonPath("$.httpMethod").value("PUT"))
                .andExpect(jsonPath("$.requiredHeaders['Content-Type']").value(CONTENT_TYPE))
                .andExpect(jsonPath("$.expiresAt").value(EXPIRES_AT_VALUE));
    }

    @ParameterizedTest(name = "invalid body case {index}")
    @MethodSource("provideInvalidBodies")
    @DisplayName("returns 400 and does not call the service when the request is invalid")
    void shouldRejectInvalidRequest(String body) throws Exception {
        mockMvc.perform(post(UPLOADS_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(mediaService, never()).createUpload(any(MediaUploadRequestDto.class));
    }

    @Test
    @DisplayName("redirects GET of an object key to a freshly-signed download url")
    void shouldRedirectToSignedDownloadUrl() throws Exception {
        String signedUrl = "http://localhost:9000/vectory-media/" + OBJECT_KEY + "?signed=1";
        when(mediaService.resolveDownloadUrl(OBJECT_KEY)).thenReturn(signedUrl);

        mockMvc.perform(get("/api/v1/media/" + OBJECT_KEY))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", signedUrl));

        verify(mediaService).resolveDownloadUrl(eq(OBJECT_KEY));
    }
}
