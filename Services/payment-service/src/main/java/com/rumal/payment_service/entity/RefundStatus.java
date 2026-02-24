package com.rumal.payment_service.entity;

public enum RefundStatus {
    REQUESTED,
    VENDOR_APPROVED,
    VENDOR_REJECTED,
    ESCALATED_TO_ADMIN,
    ADMIN_APPROVED,
    ADMIN_REJECTED,
    REFUND_PROCESSING,
    REFUND_COMPLETED,
    REFUND_FAILED
}
