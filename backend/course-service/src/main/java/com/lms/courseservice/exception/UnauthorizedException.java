package com.lms.courseservice.exception;

public final class UnauthorizedException extends LmsException {
    public UnauthorizedException(String message) {
        super(message, 403);
    }
}