package org.vectory.contentmanager.application.event;

import lombok.Builder;
import org.vectory.contentmanager.domain.enums.PostMediaType;

@Builder
public record PostMediaEvent(
        PostMediaType mediaType,
        String objectKey
) {
}
