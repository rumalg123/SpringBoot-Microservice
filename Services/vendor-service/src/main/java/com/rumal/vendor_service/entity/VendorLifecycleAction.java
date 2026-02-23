package com.rumal.vendor_service.entity;

public enum VendorLifecycleAction {
    CREATED,
    UPDATED,
    STOP_ORDERS,
    RESUME_ORDERS,
    DELETE_REQUESTED,
    DELETE_CONFIRMED,
    DELETE_CONFIRMED_LEGACY,
    RESTORED
}

