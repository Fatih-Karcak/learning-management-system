package com.lms.notificationservice.exception;

public final class EmailSendException extends LmsException {
    public EmailSendException(String message) { super(message, 502); }
}