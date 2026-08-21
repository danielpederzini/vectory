package org.vectory.recommendationmanager.infrastructure.outbound.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.vectory.recommendationmanager.application.port.EmbeddingFactory;
import org.vectory.recommendationmanager.domain.util.VectorUtils;
import org.vectory.recommendationmanager.infrastructure.config.EmbeddingProperties;

import java.util.Map;

@Slf4j
@Component
public class OpenAiEmbeddingFactory implements EmbeddingFactory {
    private static final String TOKEN_FORMAT = "Bearer %s";

    private final EmbeddingProperties embeddingProperties;
    private final RestClient restClient;

    public OpenAiEmbeddingFactory(EmbeddingProperties embeddingProperties) {
        this.embeddingProperties = embeddingProperties;

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(embeddingProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (embeddingProperties.apiKey() != null && !embeddingProperties.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, TOKEN_FORMAT.formatted(embeddingProperties.apiKey()));
        }

        this.restClient = builder.build();
    }

    @Override
    public float[] embed(String text) {
        Map<String, Object> request = Map.of(
                "model", embeddingProperties.model(),
                "input", text,
                "dimensions", embeddingProperties.dimensions()
        );

        EmbeddingResponse response = restClient.post()
                .uri(embeddingProperties.path())
                .body(request)
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("embedding provider returned no data");
        }

        float[] embedding = response.data().getFirst().embedding();

        if (embedding == null) {
            throw new IllegalStateException("embedding provider returned null embedding");
        }

        if (embedding.length != embeddingProperties.dimensions()) {
            throw new IllegalStateException(
                    "embedding provider returned unexpected dimension: %s".formatted(embedding.length));
        }

        return VectorUtils.normalize(embedding);
    }

    @Override
    public int dimensions() {
        return embeddingProperties.dimensions();
    }
}
