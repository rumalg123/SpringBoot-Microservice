package com.rumal.payment_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateVendorBankAccountRequest(
        @NotNull UUID vendorId,
        @NotBlank @Size(max = 200) String bankName,
        @Size(max = 200) String branchName,
        @Size(max = 50) String branchCode,
        @NotBlank @Size(max = 100) String accountNumber,
        @NotBlank @Size(max = 200) String accountHolderName,
        @Size(max = 20) String swiftCode
) {}
