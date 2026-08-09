package org.vectory.usermanager.domain.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends HttpStatusException {
    private static final String MESSAGE_STRING = "invalid username or password";

    public InvalidCredentialsException() {
        super(MESSAGE_STRING, HttpStatus.UNAUTHORIZED);
    }
}
