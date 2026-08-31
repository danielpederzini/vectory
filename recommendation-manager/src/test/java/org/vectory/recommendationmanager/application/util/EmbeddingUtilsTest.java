package org.vectory.recommendationmanager.application.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.vectory.recommendationmanager.domain.enums.PostMediaType;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmbeddingUtils")
class EmbeddingUtilsTest {

    private static final String DEFAULT_DESCRIPTION = "post";

    static Stream<Arguments> postDescriptions() {
        return Stream.of(
                Arguments.of("  hello world  ", PostMediaType.IMAGE, "hello world"),
                Arguments.of("   ", PostMediaType.IMAGE, "image"),
                Arguments.of(null, PostMediaType.IMAGE, "image"),
                Arguments.of(null, null, DEFAULT_DESCRIPTION)
        );
    }

    @ParameterizedTest(name = "text=\"{0}\", media={1} -> \"{2}\"")
    @MethodSource("postDescriptions")
    @DisplayName("builds the embedding description from text, falling back to media type then a default")
    void shouldBuildPostDescription(String text, PostMediaType mediaType, String expected) {
        assertThat(EmbeddingUtils.getPostDescription(text, mediaType)).isEqualTo(expected);
    }
}
