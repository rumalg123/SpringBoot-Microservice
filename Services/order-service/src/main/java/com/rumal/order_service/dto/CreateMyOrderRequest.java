package com.rumal.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateMyOrderRequest(
        UUID productId,
        Integer quantity,
        List<@Valid CreateOrderItemRequest> items,
        @NotNull UUID shippingAddressId,
        @NotNull UUID billingAddressId
) {}
