package org.vectory.contentmanager.infrastructure.config;

import java.time.Duration;

public record StoragePresignProperties(
        Duration putTtl,
        Duration getTtl
) {
}
