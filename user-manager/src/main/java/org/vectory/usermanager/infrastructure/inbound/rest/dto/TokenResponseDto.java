package org.vectory.usermanager.infrastructure.inbound.rest.dto;

import lombok.Builder;

@Builder
public record TokenResponseDto(
        String accessToken,
        long expiresIn
) {
}
