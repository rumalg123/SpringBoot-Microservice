package com.rumal.order_service.entity;

public enum OutboxEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
