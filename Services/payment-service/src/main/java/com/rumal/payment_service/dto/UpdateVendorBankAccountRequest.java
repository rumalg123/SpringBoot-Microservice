package com.rumal.payment_service.dto;

import jakarta.validation.constraints.Size;

public record UpdateVendorBankAccountRequest(
        @Size(max = 200) String bankName,
        @Size(max = 200) String branchName,
        @Size(max = 50) String branchCode,
        @Size(max = 100) String accountNumber,
        @Size(max = 200) String accountHolderName,
        @Size(max = 20) String swiftCode
) {}
