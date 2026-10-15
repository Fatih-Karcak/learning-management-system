package com.lms.courseservice.exception;

public final class CourseNotFoundException extends LmsException {
    public CourseNotFoundException(String message) {
        super(message, 404);
    }
}