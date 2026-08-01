package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.vectory.contentmanager.domain.enums.PostMediaType;

public record PostMediaCreationRequestDto(

        @NotNull
        PostMediaType mediaType,

        @NotBlank
        @Size(max = 2048)
        String mediaUrl
) {
}
