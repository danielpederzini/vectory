package org.vectory.contentmanager.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PostNotFoundException extends HttpStatusException {
    private static final String MESSAGE_STRING = "post not found: %s";

    public PostNotFoundException(UUID postId) {
        super(MESSAGE_STRING.formatted(postId), HttpStatus.NOT_FOUND);
    }
}
