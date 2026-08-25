package org.vectory.contentmanager.infrastructure.config;

import java.util.List;

public record StorageUploadProperties(
        long maxImageBytes,
        long maxVideoBytes,
        List<String> allowedImageContentTypes,
        List<String> allowedVideoContentTypes
) {
}
