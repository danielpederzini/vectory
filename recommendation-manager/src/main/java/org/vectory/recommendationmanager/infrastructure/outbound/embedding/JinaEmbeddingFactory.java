package org.vectory.recommendationmanager.infrastructure.outbound.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.vectory.recommendationmanager.application.port.EmbeddingFactory;
import org.vectory.recommendationmanager.application.port.EmbeddingRequest;
import org.vectory.recommendationmanager.domain.util.VectorUtils;
import org.vectory.recommendationmanager.infrastructure.config.EmbeddingProperties;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class JinaEmbeddingFactory implements EmbeddingFactory {
    private static final String TOKEN_FORMAT = "Bearer %s";
    private static final String DATA_URI_FORMAT = "data:%s;base64,%s";
    private static final String DEFAULT_IMAGE_CONTENT_TYPE = "application/octet-stream";
    private static final String EMBEDDING_TYPE = "float";

    private final EmbeddingProperties embeddingProperties;
    private final RestClient restClient;

    @Autowired
    public JinaEmbeddingFactory(EmbeddingProperties embeddingProperties) {
        this(embeddingProperties, RestClient.builder());
    }

    JinaEmbeddingFactory(EmbeddingProperties embeddingProperties, RestClient.Builder builder) {
        this.embeddingProperties = embeddingProperties;

        builder.baseUrl(embeddingProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (embeddingProperties.apiKey() != null && !embeddingProperties.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, TOKEN_FORMAT.formatted(embeddingProperties.apiKey()));
        }

        this.restClient = builder.build();
    }

    @Override
    public float[] embed(EmbeddingRequest request) {
        List<Map<String, Object>> input = buildInput(request);
        if (input.isEmpty()) {
            throw new IllegalArgumentException("embedding request must contain text or image");
        }

        Map<String, Object> body = Map.of(
                "model", embeddingProperties.model(),
                "dimensions", embeddingProperties.dimensions(),
                "normalized", true,
                "embedding_type", EMBEDDING_TYPE,
                "input", input
        );

        EmbeddingResponse response = restClient.post()
                .uri(embeddingProperties.path())
                .body(body)
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("embedding provider returned no data");
        }

        List<float[]> vectors = new ArrayList<>();
        for (EmbeddingItem item : response.data()) {
            float[] embedding = item.embedding();
            if (embedding == null) {
                throw new IllegalStateException("embedding provider returned null embedding");
            }
            if (embedding.length != embeddingProperties.dimensions()) {
                throw new IllegalStateException(
                        "embedding provider returned unexpected dimension: %s".formatted(embedding.length));
            }
            vectors.add(embedding);
        }

        float[] combined = vectors.size() == 1 ? vectors.getFirst() : VectorUtils.average(vectors);
        return VectorUtils.normalize(combined);
    }

    @Override
    public int dimensions() {
        return embeddingProperties.dimensions();
    }

    private List<Map<String, Object>> buildInput(EmbeddingRequest request) {
        List<Map<String, Object>> input = new ArrayList<>();
        if (request.hasText()) {
            input.add(Map.of("text", request.text().strip()));
        }
        if (request.hasImage()) {
            input.add(Map.of("image", toDataUri(request.image(), request.imageContentType())));
        }
        return input;
    }

    private String toDataUri(byte[] image, String contentType) {
        String type = contentType == null || contentType.isBlank() ? DEFAULT_IMAGE_CONTENT_TYPE : contentType;
        return DATA_URI_FORMAT.formatted(type, Base64.getEncoder().encodeToString(image));
    }
}
