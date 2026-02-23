package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CheckoutPreviewResponse(
        int itemCount,
        int totalQuantity,
        String couponCode,
        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal shippingAmount,
        BigDecimal shippingDiscountTotal,
        BigDecimal totalDiscount,
        BigDecimal grandTotal,
        List<AppliedPromotionPreviewResponse> appliedPromotions,
        List<RejectedPromotionPreviewResponse> rejectedPromotions,
        Instant pricedAt
) {
}
