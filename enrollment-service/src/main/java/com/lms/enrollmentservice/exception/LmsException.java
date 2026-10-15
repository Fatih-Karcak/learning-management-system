package com.lms.enrollmentservice.exception;

public sealed class LmsException extends RuntimeException
        permits EnrollmentNotFoundException,StorageException {

    private final int statusCode;

    public LmsException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}