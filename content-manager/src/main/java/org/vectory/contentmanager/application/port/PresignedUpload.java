package org.vectory.contentmanager.application.port;

import java.time.Instant;

public record PresignedUpload(
        String url,
        Instant expiresAt
) {
}
