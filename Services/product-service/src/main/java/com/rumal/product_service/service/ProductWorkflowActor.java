package com.rumal.product_service.service;

public enum ProductWorkflowActor {
    PLATFORM,
    VENDOR_ADMIN,
    VENDOR_STAFF;

    public boolean isPlatformPrivileged() {
        return this == PLATFORM;
    }

    public boolean canSubmitForReview() {
        return this != VENDOR_STAFF;
    }
}
