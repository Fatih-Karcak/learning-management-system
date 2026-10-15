package com.lms.notificationservice.exception;

public sealed class LmsException extends RuntimeException permits EmailSendException {
    private final int statusCode;
    public LmsException(String message, int statusCode) { super(message); this.statusCode = statusCode; }
    public int getStatusCode() { return statusCode; }
}