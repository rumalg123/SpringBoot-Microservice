package com.rumal.order_service.dto;

import java.math.BigDecimal;

public record PromotionQuoteResponse(
        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal shippingAmount,
        BigDecimal shippingDiscountTotal,
        BigDecimal totalDiscount,
        BigDecimal grandTotal
) {}
