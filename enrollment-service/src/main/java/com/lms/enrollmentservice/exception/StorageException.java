package com.lms.enrollmentservice.exception;

public final class StorageException extends LmsException {
    public StorageException(String message) { super(message, 502); }
}