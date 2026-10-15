package com.lms.userservice.exception;

public final class UserNotFoundException extends LmsException {
    public UserNotFoundException(String message) {
        super(message, 404);
    }
}
