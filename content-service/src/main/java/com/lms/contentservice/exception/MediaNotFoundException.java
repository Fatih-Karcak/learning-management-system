package com.lms.contentservice.exception;

public final class MediaNotFoundException extends LmsException {
    public MediaNotFoundException(String message) { super(message, 404); }
}