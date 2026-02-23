package com.rumal.order_service.entity;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    RETURN_REQUESTED,
    REFUND_PENDING,
    REFUNDED,
    CANCELLED,
    CLOSED
}
