package com.rumal.cart_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutCartRequest(
        @NotNull UUID shippingAddressId,
        @NotNull UUID billingAddressId,
        @Size(max = 64) String couponCode,
        @DecimalMin(value = "0.00", message = "shippingAmount cannot be negative") BigDecimal shippingAmount,
        @Size(max = 8) String countryCode
) {
}
