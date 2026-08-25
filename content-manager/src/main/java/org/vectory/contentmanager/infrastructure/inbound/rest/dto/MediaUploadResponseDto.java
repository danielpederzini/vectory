package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

@Builder
public record MediaUploadResponseDto(
        String objectKey,
        String uploadUrl,
        String httpMethod,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}
