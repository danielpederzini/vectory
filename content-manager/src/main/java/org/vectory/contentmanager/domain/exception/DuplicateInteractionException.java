package org.vectory.contentmanager.domain.exception;

import org.springframework.http.HttpStatus;
import org.vectory.contentmanager.domain.enums.InteractionType;

import java.util.UUID;

public class DuplicateInteractionException extends HttpStatusException {
    private static final String MESSAGE_STRING = "interaction %s already exists for post %s and user %s";

    public DuplicateInteractionException(UUID postId, UUID userId, InteractionType type, Throwable cause) {
        super(MESSAGE_STRING.formatted(type, postId, userId), cause, HttpStatus.CONFLICT);
    }
}
