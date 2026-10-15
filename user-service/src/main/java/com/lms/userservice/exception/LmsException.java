package com.lms.userservice.exception;

public sealed class LmsException extends RuntimeException
        permits UserNotFoundException,
        EmailAlreadyExistsException,
        UnauthorizedException {

    private final int statusCode;

    public LmsException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

