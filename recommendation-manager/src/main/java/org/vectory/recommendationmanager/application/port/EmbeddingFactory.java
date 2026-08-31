package org.vectory.recommendationmanager.application.port;

public interface EmbeddingFactory {

    float[] embed(EmbeddingRequest request);

    int dimensions();
}
