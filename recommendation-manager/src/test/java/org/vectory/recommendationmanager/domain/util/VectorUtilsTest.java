package org.vectory.recommendationmanager.domain.util;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("VectorMath")
class VectorUtilsTest {

    private static final float TOLERANCE = 1e-6f;
    private static final Offset<Float> OFFSET = Offset.offset(TOLERANCE);
    private static final double ALPHA = 0.2;

    static Stream<Arguments> normalizeCases() {
        return Stream.of(
                Arguments.of(new float[]{3.0f, 4.0f}, new float[]{0.6f, 0.8f}),
                Arguments.of(new float[]{0.0f, 0.0f}, new float[]{0.0f, 0.0f})
        );
    }

    @ParameterizedTest
    @MethodSource("normalizeCases")
    @DisplayName("normalize returns a unit-length vector (and leaves a zero vector unchanged)")
    void shouldNormalize(float[] input, float[] expected) {
        assertThat(VectorUtils.normalize(input)).containsExactly(expected, OFFSET);
    }

    @Test
    @DisplayName("average computes the element-wise mean")
    void shouldComputeAverage() {
        float[] result = VectorUtils.average(List.of(new float[]{1.0f, 2.0f}, new float[]{3.0f, 6.0f}));

        assertThat(result).containsExactly(new float[]{2.0f, 4.0f}, OFFSET);
    }

    @Test
    @DisplayName("weightedAverage weights each vector before averaging")
    void shouldComputeWeightedAverage() {
        float[] result = VectorUtils.weightedAverage(
                List.of(new float[]{1.0f, 0.0f}, new float[]{0.0f, 1.0f}),
                List.of(3.0, 1.0));

        assertThat(result).containsExactly(new float[]{0.75f, 0.25f}, OFFSET);
    }

    @Test
    @DisplayName("blend interpolates between previous and target by alpha")
    void shouldBlendByAlpha() {
        float[] result = VectorUtils.blend(new float[]{0.0f, 10.0f}, new float[]{10.0f, 0.0f}, ALPHA);

        assertThat(result).containsExactly(new float[]{2.0f, 8.0f}, OFFSET);
    }

    @Test
    @DisplayName("identifies zero vectors and formats vectors for pgvector queries")
    void shouldSupportFeedVectorOperations() {
        float[] zeroVector = {0.0f, 0.0f};
        float[] nonZeroVector = {1.5f, -0.25f};

        assertThat(VectorUtils.isZeroVector(zeroVector)).isTrue();
        assertThat(VectorUtils.isZeroVector(nonZeroVector)).isFalse();
        assertThat(VectorUtils.toPgVectorLiteral(nonZeroVector)).isEqualTo("[1.5,-0.25]");
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of("weightedAverage rejects a zero total weight",
                        (ThrowingCallable) () -> VectorUtils.weightedAverage(List.of(new float[]{1.0f}), List.of(0.0))),
                Arguments.of("average rejects an empty input",
                        (ThrowingCallable) () -> VectorUtils.average(List.of()))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    @DisplayName("rejects invalid inputs with IllegalArgumentException")
    void shouldRejectInvalidInputs(String description, ThrowingCallable call) {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(call);
    }
}
