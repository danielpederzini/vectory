package org.vectory.recommendationmanager.application.port;

public interface EmbeddingFactory {

    float[] embed(String text);

    int dimensions();
}
