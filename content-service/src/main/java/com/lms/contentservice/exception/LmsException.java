package com.lms.contentservice.exception;

public sealed class LmsException extends RuntimeException
        permits MediaNotFoundException, UnsupportedMediaException,
        StorageException, UnauthorizedMediaAccessException {

    private final int statusCode;

    public LmsException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}