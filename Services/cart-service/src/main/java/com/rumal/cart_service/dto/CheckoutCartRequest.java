package com.rumal.cart_service.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutCartRequest(
        @NotNull UUID shippingAddressId,
        @NotNull UUID billingAddressId
) {
}
