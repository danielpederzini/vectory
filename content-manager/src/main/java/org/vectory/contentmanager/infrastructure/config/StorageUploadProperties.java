package org.vectory.contentmanager.infrastructure.config;

import java.util.List;

public record StorageUploadProperties(
        long maxImageBytes,
        List<String> allowedImageContentTypes
) {
}
