package org.vectory.contentmanager.domain.exception;

import org.springframework.http.HttpStatus;

public class MediaNotFoundException extends HttpStatusException {
    private static final String MESSAGE_STRING = "media object not found in storage: %s";

    public MediaNotFoundException(String objectKey) {
        super(MESSAGE_STRING.formatted(objectKey), HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
