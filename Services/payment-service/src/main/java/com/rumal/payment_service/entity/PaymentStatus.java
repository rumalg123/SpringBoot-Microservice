package com.rumal.payment_service.entity;

public enum PaymentStatus {
    INITIATED,
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    CHARGEBACKED,
    EXPIRED
}
