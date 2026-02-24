package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID orderId,
        String invoiceNumber,
        String customerName,
        String customerEmail,
        List<InvoiceLineItem> items,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal shippingTotal,
        BigDecimal grandTotal,
        String currency,
        Instant issuedAt
) {
    public record InvoiceLineItem(
            String name,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}
}
