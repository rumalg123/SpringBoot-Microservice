package com.rumal.payment_service.exception;

public class PayHereApiException extends RuntimeException {
    public PayHereApiException(String message) { super(message); }
    public PayHereApiException(String message, Throwable cause) { super(message, cause); }
}
