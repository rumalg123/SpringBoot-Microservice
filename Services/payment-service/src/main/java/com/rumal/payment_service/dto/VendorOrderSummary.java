package com.rumal.payment_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorOrderSummary(
        UUID id,
        UUID vendorId,
        BigDecimal orderTotal,
        BigDecimal platformFee,
        BigDecimal payoutAmount,
        String status
) {}
