package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.PayoutSchedule;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpsertVendorPayoutConfigRequest(
        @NotBlank @Size(min = 3, max = 3) String payoutCurrency,
        @NotNull PayoutSchedule payoutSchedule,
        @DecimalMin("0.00") BigDecimal payoutMinimum,
        @Size(max = 180) String bankAccountHolder,
        @Size(max = 120) String bankName,
        @Size(max = 60) String bankRoutingCode,
        @Size(max = 60) String bankAccountNumberMasked,
        @Size(max = 60) String taxId
) {
}
