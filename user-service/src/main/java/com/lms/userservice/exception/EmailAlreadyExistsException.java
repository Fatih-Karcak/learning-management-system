package com.lms.userservice.exception;

public final class EmailAlreadyExistsException extends LmsException {
    public EmailAlreadyExistsException(String message) {
        super(message, 409);
    }
}
