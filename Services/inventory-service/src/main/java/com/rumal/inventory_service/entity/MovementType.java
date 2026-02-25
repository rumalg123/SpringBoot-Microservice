package com.rumal.inventory_service.entity;

public enum MovementType {
    STOCK_IN,
    STOCK_OUT,
    RESERVATION,
    RESERVATION_CONFIRM,
    RESERVATION_RELEASE,
    ADJUSTMENT,
    BULK_IMPORT
}
