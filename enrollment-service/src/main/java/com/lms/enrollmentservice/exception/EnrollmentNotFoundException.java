package com.lms.enrollmentservice.exception;

public final class EnrollmentNotFoundException extends LmsException {
    public EnrollmentNotFoundException(String message) {
        super(message, 404);
    }
}