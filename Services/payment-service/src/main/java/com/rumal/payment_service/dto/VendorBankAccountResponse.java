package com.rumal.payment_service.dto;

import java.time.Instant;
import java.util.UUID;

public record VendorBankAccountResponse(
        UUID id,
        UUID vendorId,
        String bankName,
        String branchName,
        String branchCode,
        String accountNumber,
        String accountHolderName,
        String swiftCode,
        boolean primary,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
