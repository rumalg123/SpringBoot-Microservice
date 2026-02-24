package com.rumal.access_service.entity;

public enum VendorPermission {
    PRODUCTS_MANAGE("vendor.products.manage"),
    PROMOTIONS_MANAGE("vendor.promotions.manage"),
    ORDERS_READ("vendor.orders.read"),
    ORDERS_MANAGE("vendor.orders.manage"),
    REPORTS_READ("vendor.reports.read"),
    ANALYTICS_READ("vendor.analytics.read"),
    FINANCE_READ("vendor.finance.read"),
    SETTINGS_MANAGE("vendor.settings.manage");

    private final String code;

    VendorPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
