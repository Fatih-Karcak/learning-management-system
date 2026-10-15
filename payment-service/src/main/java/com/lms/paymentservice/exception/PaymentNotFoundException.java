package com.lms.paymentservice.exception;

public final class PaymentNotFoundException extends LmsException {
    public PaymentNotFoundException(String message) {
        super(message, 404);
    }
}