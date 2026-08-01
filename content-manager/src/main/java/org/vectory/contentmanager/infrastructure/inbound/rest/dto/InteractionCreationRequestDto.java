package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import jakarta.validation.constraints.NotNull;
import org.vectory.contentmanager.domain.enums.InteractionType;

import java.util.UUID;

public record InteractionCreationRequestDto(

        @NotNull
        UUID postId,

        @NotNull
        UUID userId,

        @NotNull
        InteractionType type
) {
}
