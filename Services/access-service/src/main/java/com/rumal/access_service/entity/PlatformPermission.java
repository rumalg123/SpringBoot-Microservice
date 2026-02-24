package com.rumal.access_service.entity;

public enum PlatformPermission {
    PRODUCTS_MANAGE("platform.products.manage"),
    PROMOTIONS_READ("platform.promotions.read"),
    PROMOTIONS_MANAGE("platform.promotions.manage"),
    CATEGORIES_MANAGE("platform.categories.manage"),
    ORDERS_READ("platform.orders.read"),
    ORDERS_MANAGE("platform.orders.manage"),
    POSTERS_MANAGE("platform.posters.manage"),
    VENDORS_READ("platform.vendors.read"),
    VENDORS_MANAGE("platform.vendors.manage"),
    CUSTOMERS_READ("platform.customers.read"),
    CUSTOMERS_MANAGE("platform.customers.manage"),
    ANALYTICS_READ("platform.analytics.read"),
    SYSTEM_CONFIG("platform.system.config");

    private final String code;

    PlatformPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
