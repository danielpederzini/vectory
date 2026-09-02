package org.vectory.recommendationmanager.domain.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.StringJoiner;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VectorUtils {

    public static float[] normalize(float[] vector) {
        double sumOfSquares = 0.0;
        for (float value : vector) {
            sumOfSquares += (double) value * value;
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm == 0.0) {
            return vector.clone();
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    public static float[] zeros(int dimensions) {
        return new float[dimensions];
    }

    public static boolean isZeroVector(float[] vector) {
        for (float value : vector) {
            if (value != 0.0f) {
                return false;
            }
        }
        return true;
    }

    public static String toPgVectorLiteral(float[] vector) {
        StringJoiner vectorComponents = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            vectorComponents.add(Float.toString(value));
        }
        return vectorComponents.toString();
    }

    public static float[] average(java.util.List<float[]> vectors) {
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("cannot average an empty list of vectors");
        }
        int dimensions = vectors.getFirst().length;
        double[] accumulator = new double[dimensions];
        for (float[] vector : vectors) {
            requireDimension(vector, dimensions);
            for (int i = 0; i < dimensions; i++) {
                accumulator[i] += vector[i];
            }
        }
        float[] result = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            result[i] = (float) (accumulator[i] / vectors.size());
        }
        return result;
    }

    public static float[] weightedAverage(java.util.List<float[]> vectors, java.util.List<Double> weights) {
        if (vectors.isEmpty()) {
            throw new IllegalArgumentException("cannot average an empty list of vectors");
        }
        if (vectors.size() != weights.size()) {
            throw new IllegalArgumentException("vectors and weights must have the same size");
        }
        int dimensions = vectors.getFirst().length;
        double[] accumulator = new double[dimensions];
        double weightSum = 0.0;
        for (int v = 0; v < vectors.size(); v++) {
            float[] vector = vectors.get(v);
            double weight = weights.get(v);
            requireDimension(vector, dimensions);
            weightSum += weight;
            for (int i = 0; i < dimensions; i++) {
                accumulator[i] += weight * vector[i];
            }
        }
        if (weightSum == 0.0) {
            throw new IllegalArgumentException("sum of weights must be non-zero");
        }
        float[] result = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            result[i] = (float) (accumulator[i] / weightSum);
        }
        return result;
    }

    public static float[] blend(float[] previous, float[] target, double alpha) {
        requireDimension(target, previous.length);
        float[] result = new float[previous.length];
        for (int i = 0; i < previous.length; i++) {
            result[i] = (float) (previous[i] * (1.0 - alpha) + target[i] * alpha);
        }
        return result;
    }

    private static void requireDimension(float[] vector, int dimensions) {
        if (vector.length != dimensions) {
            throw new IllegalArgumentException(
                    "vector dimension mismatch: expected %d but was %d".formatted(dimensions, vector.length));
        }
    }
}
