package com.lms.contentservice.exception;

public final class UnsupportedMediaException extends LmsException {
    public UnsupportedMediaException(String message) { super(message, 415); }
}