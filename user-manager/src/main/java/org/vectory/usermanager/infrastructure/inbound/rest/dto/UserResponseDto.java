package org.vectory.usermanager.infrastructure.inbound.rest.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record UserResponseDto(
        UUID id,
        String username,
        String email,
        Instant creationInstant
) {
}
