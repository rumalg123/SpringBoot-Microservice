package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PromotionCheckoutPricingRequest(
        UUID couponReservationId,
        String couponCode,
        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal shippingAmount,
        BigDecimal shippingDiscountTotal,
        BigDecimal totalDiscount,
        BigDecimal grandTotal
) {
}
