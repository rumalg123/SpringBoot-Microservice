package com.rumal.order_service.entity;

public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    CONFIRMED,
    ON_HOLD,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    RETURN_REQUESTED,
    RETURN_REJECTED,
    REFUND_PENDING,
    REFUNDED,
    CANCELLED,
    CLOSED
}
