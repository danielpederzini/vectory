package org.vectory.recommendationmanager.application.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.vectory.recommendationmanager.domain.enums.PostMediaType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingUtils {
    private static final String DEFAULT_POST_DESCRIPTION = "post";

    public static String getPostDescription(String text, PostMediaType mediaType) {
        if (text != null && !text.isBlank()) {
            return text.strip();
        }

        if (mediaType != null) {
            return mediaType.name().toLowerCase();
        }

        return DEFAULT_POST_DESCRIPTION;
    }
}
