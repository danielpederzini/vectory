package org.vectory.contentmanager.domain.exception;

import org.springframework.http.HttpStatus;

public class InvalidMediaException extends HttpStatusException {
    public InvalidMediaException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
