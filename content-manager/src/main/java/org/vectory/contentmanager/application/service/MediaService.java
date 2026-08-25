package org.vectory.contentmanager.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.vectory.contentmanager.application.port.MediaStoragePort;
import org.vectory.contentmanager.application.port.PresignedUpload;
import org.vectory.contentmanager.domain.enums.PostMediaType;
import org.vectory.contentmanager.domain.exception.InvalidMediaException;
import org.vectory.contentmanager.infrastructure.config.StorageProperties;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadResponseDto;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final String HTTP_METHOD = "PUT";
    private static final String KEY_PREFIX = "posts";

    private final MediaStoragePort mediaStoragePort;
    private final StorageProperties storageProperties;

    public MediaUploadResponseDto createUpload(MediaUploadRequestDto request) {
        String contentType = request.contentType().toLowerCase(Locale.ROOT);
        validate(request.mediaType(), contentType, request.sizeBytes());

        String objectKey = buildObjectKey(contentType);
        PresignedUpload upload = mediaStoragePort.createUploadUrl(objectKey, contentType);

        return MediaUploadResponseDto.builder()
                .objectKey(objectKey)
                .uploadUrl(upload.url())
                .httpMethod(HTTP_METHOD)
                .requiredHeaders(Map.of("Content-Type", contentType))
                .expiresAt(upload.expiresAt())
                .build();
    }

    public String resolveDownloadUrl(String objectKey) {
        return mediaStoragePort.createDownloadUrl(objectKey);
    }

    private void validate(PostMediaType mediaType, String contentType, long sizeBytes) {
        StorageProperties.Upload upload = storageProperties.upload();
        List<String> allowedTypes = allowedContentTypes(mediaType, upload);
        long maxBytes = maxBytes(mediaType, upload);

        if (!allowedTypes.contains(contentType)) {
            throw new InvalidMediaException(
                    "unsupported content type '%s' for %s; allowed: %s"
                            .formatted(contentType, mediaType, allowedTypes));
        }

        if (sizeBytes > maxBytes) {
            throw new InvalidMediaException(
                    "file size %d exceeds maximum %d for %s".formatted(sizeBytes, maxBytes, mediaType));
        }
    }

    private List<String> allowedContentTypes(PostMediaType mediaType, StorageProperties.Upload upload) {
        return mediaType == PostMediaType.VIDEO
                ? upload.allowedVideoContentTypes()
                : upload.allowedImageContentTypes();
    }

    private long maxBytes(PostMediaType mediaType, StorageProperties.Upload upload) {
        return mediaType == PostMediaType.VIDEO ? upload.maxVideoBytes() : upload.maxImageBytes();
    }

    private String buildObjectKey(String contentType) {
        return "%s/%s.%s".formatted(KEY_PREFIX, UUID.randomUUID(), extensionFor(contentType));
    }

    private String extensionFor(String contentType) {
        String subtype = contentType.substring(contentType.indexOf('/') + 1);
        return switch (subtype) {
            case "jpeg" -> "jpg";
            case "quicktime" -> "mov";
            case "x-matroska" -> "mkv";
            default -> subtype;
        };
    }
}
