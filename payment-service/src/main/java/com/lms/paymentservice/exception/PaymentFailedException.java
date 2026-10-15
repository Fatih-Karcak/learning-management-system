package com.lms.paymentservice.exception;

public final class PaymentFailedException extends LmsException {
    public PaymentFailedException(String message) {
        super(message, 422);
    }
}