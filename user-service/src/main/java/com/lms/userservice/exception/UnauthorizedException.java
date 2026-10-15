package com.lms.userservice.exception;

public final class UnauthorizedException extends LmsException {
    public UnauthorizedException(String message) {
        super(message, 403);
    }
}
