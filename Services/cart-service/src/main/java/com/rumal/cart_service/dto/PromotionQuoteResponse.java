package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PromotionQuoteResponse(
        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal shippingAmount,
        BigDecimal shippingDiscountTotal,
        BigDecimal totalDiscount,
        BigDecimal grandTotal,
        List<PromotionQuoteLineResponse> lines,
        List<AppliedPromotionQuoteEntry> appliedPromotions,
        List<RejectedPromotionQuoteEntry> rejectedPromotions,
        Instant pricedAt
) {
}
