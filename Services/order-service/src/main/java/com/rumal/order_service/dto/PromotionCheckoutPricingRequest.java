package com.rumal.order_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PromotionCheckoutPricingRequest(
        UUID couponReservationId,
        @Size(max = 64) String couponCode,
        @NotNull(message = "subtotal is required")
        @DecimalMin(value = "0.00", message = "subtotal cannot be negative") BigDecimal subtotal,
        @NotNull(message = "lineDiscountTotal is required")
        @DecimalMin(value = "0.00", message = "lineDiscountTotal cannot be negative") BigDecimal lineDiscountTotal,
        @NotNull(message = "cartDiscountTotal is required")
        @DecimalMin(value = "0.00", message = "cartDiscountTotal cannot be negative") BigDecimal cartDiscountTotal,
        @NotNull(message = "shippingAmount is required")
        @DecimalMin(value = "0.00", message = "shippingAmount cannot be negative") BigDecimal shippingAmount,
        @NotNull(message = "shippingDiscountTotal is required")
        @DecimalMin(value = "0.00", message = "shippingDiscountTotal cannot be negative") BigDecimal shippingDiscountTotal,
        @NotNull(message = "totalDiscount is required")
        @DecimalMin(value = "0.00", message = "totalDiscount cannot be negative") BigDecimal totalDiscount,
        @NotNull(message = "grandTotal is required")
        @DecimalMin(value = "0.01", message = "grandTotal must be positive")
        BigDecimal grandTotal
) {
}
