package org.vectory.usermanager.domain.exception;

import org.springframework.http.HttpStatus;

public class DuplicateUserException extends HttpStatusException {
    private static final String MESSAGE_STRING = "user already exists with username %s or email %s";

    public DuplicateUserException(String username, String email, Throwable cause) {
        super(MESSAGE_STRING.formatted(username, email), cause, HttpStatus.CONFLICT);
    }
}
