package com.lms.contentservice.exception;

public final class StorageException extends LmsException {
    public StorageException(String message) { super(message, 502); }
}