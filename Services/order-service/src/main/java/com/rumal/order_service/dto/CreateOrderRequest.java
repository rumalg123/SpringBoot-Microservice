package com.rumal.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        UUID productId,
        Integer quantity,
        List<@Valid CreateOrderItemRequest> items,
        @NotNull UUID shippingAddressId,
        @NotNull UUID billingAddressId,
        @Size(max = 500) String customerNote
) {}
