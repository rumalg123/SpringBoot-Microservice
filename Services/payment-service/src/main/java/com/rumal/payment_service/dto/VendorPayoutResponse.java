package com.rumal.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendorPayoutResponse(
        UUID id,
        UUID vendorId,
        BigDecimal payoutAmount,
        BigDecimal platformFee,
        String currency,
        String vendorOrderIds,
        String bankNameSnapshot,
        String accountNumberSnapshot,
        String accountHolderSnapshot,
        String status,
        String referenceNumber,
        String approvedBy,
        String completedBy,
        String adminNote,
        Instant approvedAt,
        Instant completedAt,
        Instant createdAt
) {}
