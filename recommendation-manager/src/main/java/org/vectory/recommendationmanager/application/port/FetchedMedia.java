package org.vectory.recommendationmanager.application.port;

public record FetchedMedia(
        byte[] bytes,
        String contentType
) {
}
