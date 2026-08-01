package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PostCreationRequestDto(

        @NotNull
        UUID authorId,

        @Size(max = 5000)
        String text,

        @Valid
        PostMediaCreationRequestDto media
) {
}
