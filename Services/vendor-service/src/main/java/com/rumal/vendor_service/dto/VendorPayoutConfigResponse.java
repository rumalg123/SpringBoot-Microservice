package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.PayoutSchedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendorPayoutConfigResponse(
        UUID id,
        UUID vendorId,
        String payoutCurrency,
        PayoutSchedule payoutSchedule,
        BigDecimal payoutMinimum,
        String bankAccountHolder,
        String bankName,
        String bankRoutingCode,
        String bankAccountNumberMasked,
        String taxId,
        Instant createdAt,
        Instant updatedAt
) {
}
