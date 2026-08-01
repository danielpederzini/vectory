package org.vectory.contentmanager.infrastructure.inbound.rest.dto;

import lombok.Builder;
import org.vectory.contentmanager.domain.enums.PostMediaType;

@Builder
public record PostMediaResponseDto(
        PostMediaType mediaType,
        String mediaUrl
) {
}
