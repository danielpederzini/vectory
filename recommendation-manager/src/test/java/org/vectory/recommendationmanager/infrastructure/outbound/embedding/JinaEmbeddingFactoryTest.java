package org.vectory.recommendationmanager.infrastructure.outbound.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.vectory.recommendationmanager.application.port.EmbeddingRequest;
import org.vectory.recommendationmanager.infrastructure.config.EmbeddingProperties;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

@DisplayName("JinaEmbeddingFactory")
class JinaEmbeddingFactoryTest {

    private static final String BASE_URL = "https://api.jina.ai";
    private static final String PATH = "/v1/embeddings";
    private static final String API_KEY = "jina-key";
    private static final String MODEL = "jina-clip-v2";
    private static final int DIMENSIONS = 3;
    private static final String ENDPOINT = BASE_URL + PATH;

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private JinaEmbeddingFactory factory;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        EmbeddingProperties properties = new EmbeddingProperties(BASE_URL, PATH, API_KEY, MODEL, DIMENSIONS);
        factory = new JinaEmbeddingFactory(properties, builder);
    }

    @Test
    @DisplayName("sends a single text input and returns the normalized vector")
    void shouldEmbedTextOnly() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.dimensions").value(DIMENSIONS))
                .andExpect(jsonPath("$.input[0].text").value("hello"))
                .andExpect(jsonPath("$.input[1]").doesNotExist())
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[3.0,4.0,0.0]}]}", MediaType.APPLICATION_JSON));

        float[] result = factory.embed(EmbeddingRequest.ofText("hello"));

        assertThat(result).containsExactly(0.6f, 0.8f, 0.0f);
        server.verify();
    }

    @Test
    @DisplayName("sends text and a base64 image, averaging the two returned vectors and normalizing")
    void shouldEmbedTextAndImageAndAverage() {
        byte[] image = "img".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.input[0].text").value("a cat"))
                .andExpect(jsonPath("$.input[1].image").value("data:image/png;base64,aW1n"))
                .andRespond(withSuccess(
                        "{\"data\":[{\"embedding\":[1.0,0.0,0.0]},{\"embedding\":[0.0,1.0,0.0]}]}",
                        MediaType.APPLICATION_JSON));

        float[] result = factory.embed(new EmbeddingRequest("a cat", image, "image/png"));

        // average = [0.5, 0.5, 0] -> normalized = [0.7071, 0.7071, 0]
        assertThat(result[0]).isCloseTo(0.70710677f, org.assertj.core.api.Assertions.within(1e-6f));
        assertThat(result[1]).isCloseTo(0.70710677f, org.assertj.core.api.Assertions.within(1e-6f));
        assertThat(result[2]).isEqualTo(0.0f);
        server.verify();
    }

    @Test
    @DisplayName("rejects an embedding whose dimension does not match the configured size")
    void shouldRejectUnexpectedDimension() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"data\":[{\"embedding\":[1.0,2.0]}]}", MediaType.APPLICATION_JSON));

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> factory.embed(EmbeddingRequest.ofText("hello")))
                .isInstanceOf(IllegalStateException.class);
    }
}
