package org.vectory.recommendationmanager.infrastructure.outbound.storage;

import org.springframework.stereotype.Component;
import org.vectory.recommendationmanager.application.port.FetchedMedia;
import org.vectory.recommendationmanager.application.port.MediaFetchProvider;
import org.vectory.recommendationmanager.infrastructure.config.StorageProperties;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Component
public class S3MediaFetchProvider implements MediaFetchProvider {

    private final S3Client s3Client;
    private final StorageProperties properties;

    public S3MediaFetchProvider(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public FetchedMedia fetch(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();

        ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(request);
        return new FetchedMedia(object.asByteArray(), object.response().contentType());
    }
}
