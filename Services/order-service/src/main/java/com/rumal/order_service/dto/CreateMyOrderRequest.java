package com.rumal.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateMyOrderRequest(
        UUID productId,
        Integer quantity,
        @Size(max = 100, message = "Cannot exceed 100 items per order")
        List<@Valid CreateOrderItemRequest> items,
        @NotNull UUID shippingAddressId,
        @NotNull UUID billingAddressId,
        @Valid PromotionCheckoutPricingRequest promotionPricing,
        @Size(max = 500) String customerNote
) {}
