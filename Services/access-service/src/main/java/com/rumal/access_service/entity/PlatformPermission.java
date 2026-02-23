package com.rumal.access_service.entity;

public enum PlatformPermission {
    PRODUCTS_MANAGE("platform.products.manage"),
    PROMOTIONS_MANAGE("platform.promotions.manage"),
    CATEGORIES_MANAGE("platform.categories.manage"),
    ORDERS_READ("platform.orders.read"),
    ORDERS_MANAGE("platform.orders.manage"),
    POSTERS_MANAGE("platform.posters.manage");

    private final String code;

    PlatformPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
