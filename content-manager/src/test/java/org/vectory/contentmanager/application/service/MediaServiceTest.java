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
import org.vectory.contentmanager.infrastructure.config.StoragePresignProperties;
import org.vectory.contentmanager.infrastructure.config.StorageProperties;
import org.vectory.contentmanager.infrastructure.config.StorageUploadProperties;
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

    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CONTENT_TYPE_QUICKTIME = "video/quicktime";
    private static final String CONTENT_TYPE_QUICKTIME_MIXED_CASE = "VIDEO/QuickTime";
    private static final String CONTENT_TYPE_MP4 = "video/mp4";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final List<String> ALLOWED_IMAGE_CONTENT_TYPES =
            List.of(CONTENT_TYPE_JPEG, CONTENT_TYPE_PNG, "image/webp", "image/gif");
    private static final List<String> ALLOWED_VIDEO_CONTENT_TYPES =
            List.of(CONTENT_TYPE_MP4, "video/webm", CONTENT_TYPE_QUICKTIME);

    private static final String OBJECT_KEY_PREFIX = "posts/";
    private static final String JPG_EXTENSION = ".jpg";
    private static final String MOV_EXTENSION = ".mov";
    private static final String EXISTING_OBJECT_KEY = "posts/2b0f0c8e-cat.png";

    private static final long MAX_IMAGE_BYTES = 10_485_760L;
    private static final long MAX_VIDEO_BYTES = 104_857_600L;
    private static final long SMALL_IMAGE_SIZE = 2_048L;
    private static final long SMALL_VIDEO_SIZE = 4_096L;
    private static final long OVERSIZE_IMAGE = MAX_IMAGE_BYTES + 1;

    private static final String INTERNAL_ENDPOINT = "http://minio:9000";
    private static final String PUBLIC_ENDPOINT = "http://localhost:9000";
    private static final String REGION = "us-east-1";
    private static final String BUCKET = "vectory-media";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final Duration PUT_TTL = Duration.ofMinutes(10);
    private static final Duration GET_TTL = Duration.ofMinutes(15);

    private static final String PRESIGNED_URL = "http://localhost:9000/vectory-media/posts/x.jpg?signed=1";
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-15T10:15:30Z");

    @Mock
    private MediaStoragePort mediaStoragePort;

    private MediaService mediaService;

    @Captor
    private ArgumentCaptor<String> objectKeyCaptor;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                INTERNAL_ENDPOINT,
                PUBLIC_ENDPOINT,
                REGION,
                BUCKET,
                ACCESS_KEY,
                SECRET_KEY,
                true,
                new StoragePresignProperties(PUT_TTL, GET_TTL),
                new StorageUploadProperties(
                        MAX_IMAGE_BYTES,
                        MAX_VIDEO_BYTES,
                        ALLOWED_IMAGE_CONTENT_TYPES,
                        ALLOWED_VIDEO_CONTENT_TYPES)
        );
        mediaService = new MediaService(mediaStoragePort, properties);
    }

    @Test
    @DisplayName("generates a prefixed key with a content-type extension and returns the presigned upload")
    void shouldGenerateKeyAndReturnPresignedUpload() {
        when(mediaStoragePort.createUploadUrl(anyString(), eq(CONTENT_TYPE_JPEG)))
                .thenReturn(new PresignedUpload(PRESIGNED_URL, EXPIRES_AT));

        MediaUploadResponseDto response = mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.IMAGE, CONTENT_TYPE_JPEG, SMALL_IMAGE_SIZE));

        verify(mediaStoragePort).createUploadUrl(objectKeyCaptor.capture(), eq(CONTENT_TYPE_JPEG));
        assertThat(objectKeyCaptor.getValue()).startsWith(OBJECT_KEY_PREFIX).endsWith(JPG_EXTENSION);
        assertThat(response.objectKey()).isEqualTo(objectKeyCaptor.getValue());
        assertThat(response.uploadUrl()).isEqualTo(PRESIGNED_URL);
        assertThat(response.httpMethod()).isEqualTo("PUT");
        assertThat(response.requiredHeaders()).containsEntry(CONTENT_TYPE_HEADER, CONTENT_TYPE_JPEG);
        assertThat(response.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("normalizes the content type to lower case and maps known extensions")
    void shouldNormalizeContentTypeAndMapExtension() {
        when(mediaStoragePort.createUploadUrl(anyString(), eq(CONTENT_TYPE_QUICKTIME)))
                .thenReturn(new PresignedUpload(PRESIGNED_URL, EXPIRES_AT));

        MediaUploadResponseDto response = mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.VIDEO, CONTENT_TYPE_QUICKTIME_MIXED_CASE, SMALL_VIDEO_SIZE));

        assertThat(response.objectKey()).endsWith(MOV_EXTENSION);
        assertThat(response.requiredHeaders()).containsEntry(CONTENT_TYPE_HEADER, CONTENT_TYPE_QUICKTIME);
    }

    @Test
    @DisplayName("rejects a content type that is not allowed for the media type")
    void shouldRejectDisallowedContentType() {
        assertThatThrownBy(() -> mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.IMAGE, CONTENT_TYPE_MP4, SMALL_IMAGE_SIZE)))
                .isInstanceOf(InvalidMediaException.class);

        verifyNoInteractions(mediaStoragePort);
    }

    @Test
    @DisplayName("rejects a file that exceeds the configured maximum size")
    void shouldRejectOversizeFile() {
        assertThatThrownBy(() -> mediaService.createUpload(
                new MediaUploadRequestDto(PostMediaType.IMAGE, CONTENT_TYPE_PNG, OVERSIZE_IMAGE)))
                .isInstanceOf(InvalidMediaException.class);

        verify(mediaStoragePort, never()).createUploadUrl(anyString(), anyString());
    }

    @Test
    @DisplayName("delegates download url resolution to the storage port")
    void shouldResolveDownloadUrl() {
        when(mediaStoragePort.createDownloadUrl(EXISTING_OBJECT_KEY)).thenReturn(PRESIGNED_URL);

        assertThat(mediaService.resolveDownloadUrl(EXISTING_OBJECT_KEY)).isEqualTo(PRESIGNED_URL);
    }
}
