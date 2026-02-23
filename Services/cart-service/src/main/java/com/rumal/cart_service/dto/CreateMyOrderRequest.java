package com.rumal.cart_service.dto;

import java.util.List;
import java.util.UUID;

public record CreateMyOrderRequest(
        List<CreateMyOrderItemRequest> items,
        UUID shippingAddressId,
        UUID billingAddressId,
        PromotionCheckoutPricingRequest promotionPricing
) {
}
