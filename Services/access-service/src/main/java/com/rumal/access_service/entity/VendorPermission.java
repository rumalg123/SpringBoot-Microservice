package com.rumal.access_service.entity;

public enum VendorPermission {
    PRODUCTS_MANAGE("vendor.products.manage"),
    ORDERS_READ("vendor.orders.read"),
    ORDERS_MANAGE("vendor.orders.manage");

    private final String code;

    VendorPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
