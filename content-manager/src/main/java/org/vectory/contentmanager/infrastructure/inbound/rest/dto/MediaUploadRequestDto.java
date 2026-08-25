package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.vectory.contentmanager.domain.enums.PostMediaType;

public record MediaUploadRequestDto(

        @NotNull
        PostMediaType mediaType,

        @NotBlank
        String contentType,

        @Positive
        long sizeBytes
) {
}
