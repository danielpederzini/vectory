package org.vectory.recommendationmanager.application.port;

public record EmbeddingRequest(
        String text,
        byte[] image,
        String imageContentType
) {

    public static EmbeddingRequest ofText(String text) {
        return new EmbeddingRequest(text, null, null);
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public boolean hasImage() {
        return image != null && image.length > 0;
    }
}
