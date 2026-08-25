package org.vectory.contentmanager.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vectory.contentmanager.application.port.MediaStoragePort;
import org.vectory.contentmanager.application.port.PresignedUpload;
import org.vectory.contentmanager.domain.enums.PostMediaType;
import org.vectory.contentmanager.domain.exception.InvalidMediaException;
import org.vectory.contentmanager.infrastructure.config.StorageProperties;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadResponseDto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaService")
class MediaServiceTest {

    private static final long MAX_IMAGE_BYTES = 10_485_760L;
    private static final long MAX_VIDEO_BYTES = 104_857_600L;
    private static final String UPLOAD_URL = "http://localhost:9000/vectory-media/posts/x.jpg?signed=1";
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-15T10:15:30Z");

    @Mock
    private MediaStoragePort mediaStoragePort;

    private MediaService mediaService;

    @Captor
    private ArgumentCaptor<String> objectKeyCaptor;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "http://minio:9000",
                "http://localhost:9000",
                "us-east-1",
                "vectory-media",
                "minioadmin",
                "minioadmin",
                true,
                new StorageProperties.Presign(Duration.ofMinutes(10), Duration.ofMinutes(15)),
                new StorageProperties.Upload(
                        MAX_IMAGE_BYTES,
                        MAX_VIDEO_BYTES,
                        List.of("image/jpeg", "image/png", "image/webp", "image/gif"),
                        List.of("video/mp4", "video/webm", "video/quicktime"))
        );
        mediaService = new MediaService(mediaStoragePort, properties);
    }

    @Test
    @DisplayName("generates a prefixed key with a content-type extension and returns the presigned upload")
    void shouldGenerateKeyAndReturnPresignedUpload() {
        when(mediaStoragePort.createUploadUrl(anyString(), eq("image/jpeg")))
                .thenReturn(new PresignedUpload(UPLOAD_URL, EXPIRES_AT));

        MediaUploadResponseDto response = mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.IMAGE, "image/jpeg", 2048));

        verify(mediaStoragePort).createUploadUrl(objectKeyCaptor.capture(), eq("image/jpeg"));
        assertThat(objectKeyCaptor.getValue()).startsWith("posts/").endsWith(".jpg");
        assertThat(response.objectKey()).isEqualTo(objectKeyCaptor.getValue());
        assertThat(response.uploadUrl()).isEqualTo(UPLOAD_URL);
        assertThat(response.httpMethod()).isEqualTo("PUT");
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "image/jpeg");
        assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("normalizes the content type to lower case and maps known extensions")
    void shouldNormalizeContentTypeAndMapExtension() {
        when(mediaStoragePort.createUploadUrl(anyString(), eq("video/quicktime")))
                .thenReturn(new PresignedUpload(UPLOAD_URL, EXPIRES_AT));

        MediaUploadResponseDto response = mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.VIDEO, "VIDEO/QuickTime", 4096));

        assertThat(response.objectKey()).endsWith(".mov");
        assertThat(response.requiredHeaders()).containsEntry("Content-Type", "video/quicktime");
    }

    @Test
    @DisplayName("rejects a content type that is not allowed for the media type")
    void shouldRejectDisallowedContentType() {
        assertThatThrownBy(() -> mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.IMAGE, "video/mp4", 2048)))
                .isInstanceOf(InvalidMediaException.class);

        verifyNoInteractions(mediaStoragePort);
    }

    @Test
    @DisplayName("rejects a file that exceeds the configured maximum size")
    void shouldRejectOversizeFile() {
        assertThatThrownBy(() -> mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.IMAGE, "image/png", MAX_IMAGE_BYTES + 1)))
                .isInstanceOf(InvalidMediaException.class);

        verify(mediaStoragePort, never()).createUploadUrl(anyString(), anyString());
    }

    @Test
    @DisplayName("delegates download url resolution to the storage port")
    void shouldResolveDownloadUrl() {
        String objectKey = "posts/2b0f0c8e-cat.png";
        when(mediaStoragePort.createDownloadUrl(objectKey)).thenReturn(UPLOAD_URL);

        assertThat(mediaService.resolveDownloadUrl(objectKey)).isEqualTo(UPLOAD_URL);
    }
}
