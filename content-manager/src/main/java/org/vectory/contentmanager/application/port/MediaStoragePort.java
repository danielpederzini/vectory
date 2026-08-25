package org.vectory.contentmanager.application.port;

public interface MediaStoragePort {

    PresignedUpload createUploadUrl(String objectKey, String contentType);

    boolean objectExists(String objectKey);

    String createDownloadUrl(String objectKey);
}
