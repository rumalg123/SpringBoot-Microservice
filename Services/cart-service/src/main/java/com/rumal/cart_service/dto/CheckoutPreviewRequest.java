package com.rumal.cart_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CheckoutPreviewRequest(
        @Size(max = 64) String couponCode,
        @DecimalMin(value = "0.00", message = "shippingAmount cannot be negative") BigDecimal shippingAmount,
        @Size(max = 8) String countryCode
) {
}
