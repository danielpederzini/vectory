package org.vectory.recommendationmanager.application.port;

public interface MediaFetchProvider {

    FetchedMedia fetch(String objectKey);
}
