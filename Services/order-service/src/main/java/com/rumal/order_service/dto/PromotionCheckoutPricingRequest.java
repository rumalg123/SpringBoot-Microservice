package com.rumal.order_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PromotionCheckoutPricingRequest(
        UUID couponReservationId,
        @Size(max = 64) String couponCode,
        @DecimalMin(value = "0.00", message = "subtotal cannot be negative") BigDecimal subtotal,
        @DecimalMin(value = "0.00", message = "lineDiscountTotal cannot be negative") BigDecimal lineDiscountTotal,
        @DecimalMin(value = "0.00", message = "cartDiscountTotal cannot be negative") BigDecimal cartDiscountTotal,
        @DecimalMin(value = "0.00", message = "shippingAmount cannot be negative") BigDecimal shippingAmount,
        @DecimalMin(value = "0.00", message = "shippingDiscountTotal cannot be negative") BigDecimal shippingDiscountTotal,
        @DecimalMin(value = "0.00", message = "totalDiscount cannot be negative") BigDecimal totalDiscount,
        @DecimalMin(value = "0.00", message = "grandTotal cannot be negative") BigDecimal grandTotal
) {
}
