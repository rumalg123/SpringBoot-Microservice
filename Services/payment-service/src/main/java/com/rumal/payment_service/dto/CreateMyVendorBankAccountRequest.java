package com.rumal.payment_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMyVendorBankAccountRequest(
        @NotBlank @Size(max = 200) String bankName,
        @Size(max = 200) String branchName,
        @Size(max = 50) String branchCode,
        @NotBlank @Size(max = 100) String accountNumber,
        @NotBlank @Size(max = 200) String accountHolderName,
        @Size(max = 20) String swiftCode
) {}
