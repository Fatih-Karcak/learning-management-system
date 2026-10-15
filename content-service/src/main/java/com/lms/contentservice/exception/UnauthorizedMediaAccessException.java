package com.lms.contentservice.exception;

public final class UnauthorizedMediaAccessException extends LmsException {
    public UnauthorizedMediaAccessException(String message) { super(message, 403); }
}