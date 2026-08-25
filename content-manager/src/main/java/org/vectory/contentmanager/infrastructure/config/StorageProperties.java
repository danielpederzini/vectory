package org.vectory.contentmanager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "content-manager.storage")
public record StorageProperties(
        String endpoint,
        String publicEndpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        Presign presign,
        Upload upload
) {

    public record Presign(
            Duration putTtl,
            Duration getTtl
    ) {
    }

    public record Upload(
            long maxImageBytes,
            long maxVideoBytes,
            List<String> allowedImageContentTypes,
            List<String> allowedVideoContentTypes
    ) {
    }
}
