package org.vectory.contentmanager.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.vectory.contentmanager.application.port.MediaStoragePort;
import org.vectory.contentmanager.application.port.PresignedUpload;
import org.vectory.contentmanager.domain.enums.PostMediaType;
import org.vectory.contentmanager.domain.exception.InvalidMediaException;
import org.vectory.contentmanager.infrastructure.config.StorageProperties;
import org.vectory.contentmanager.infrastructure.config.StorageUploadProperties;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadRequestDto;
import org.vectory.contentmanager.infrastructure.inbound.rest.dto.MediaUploadResponseDto;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final String UPLOAD_HTTP_METHOD = "PUT";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String OBJECT_KEY_PREFIX = "posts";
    private static final String OBJECT_KEY_FORMAT = "%s/%s.%s";
    private static final char CONTENT_TYPE_SUBTYPE_SEPARATOR = '/';

    private static final String UNSUPPORTED_CONTENT_TYPE_MESSAGE =
            "unsupported content type '%s' for %s; allowed: %s";
    private static final String FILE_TOO_LARGE_MESSAGE =
            "file size %d exceeds maximum %d for %s";

    private static final Map<String, String> EXTENSION_BY_SUBTYPE = Map.of(
            "jpeg", "jpg"
    );

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
                .httpMethod(UPLOAD_HTTP_METHOD)
                .requiredHeaders(Map.of(CONTENT_TYPE_HEADER, contentType))
                .expiresAt(upload.expiresAt())
                .build();
    }

    public String resolveDownloadUrl(String objectKey) {
        return mediaStoragePort.createDownloadUrl(objectKey);
    }

    private void validate(PostMediaType mediaType, String contentType, long sizeBytes) {
        StorageUploadProperties upload = storageProperties.upload();
        List<String> allowedTypes = upload.allowedImageContentTypes();
        long maxBytes = upload.maxImageBytes();

        if (!allowedTypes.contains(contentType)) {
            throw new InvalidMediaException(
                    UNSUPPORTED_CONTENT_TYPE_MESSAGE.formatted(contentType, mediaType, allowedTypes));
        }

        if (sizeBytes > maxBytes) {
            throw new InvalidMediaException(
                    FILE_TOO_LARGE_MESSAGE.formatted(sizeBytes, maxBytes, mediaType));
        }
    }

    private String buildObjectKey(String contentType) {
        return OBJECT_KEY_FORMAT.formatted(OBJECT_KEY_PREFIX, UUID.randomUUID(), extensionFor(contentType));
    }

    private String extensionFor(String contentType) {
        String subtype = contentType.substring(contentType.indexOf(CONTENT_TYPE_SUBTYPE_SEPARATOR) + 1);
        return EXTENSION_BY_SUBTYPE.getOrDefault(subtype, subtype);
    }
}
