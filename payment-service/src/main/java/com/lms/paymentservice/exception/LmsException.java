package com.lms.paymentservice.exception;

public sealed class LmsException extends RuntimeException
        permits PaymentNotFoundException, PaymentFailedException {

    private final int statusCode;

    public LmsException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
}