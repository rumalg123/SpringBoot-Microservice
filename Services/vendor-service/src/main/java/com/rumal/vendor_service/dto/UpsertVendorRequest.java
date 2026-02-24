package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record UpsertVendorRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 180) String slug,
        @NotBlank @Email @Size(max = 180) String contactEmail,
        @Email @Size(max = 180) String supportEmail,
        @Size(max = 40) String contactPhone,
        @Size(max = 120) String contactPersonName,
        @Size(max = 260) String logoImage,
        @Size(max = 260) String bannerImage,
        @Size(max = 255) String websiteUrl,
        @Size(max = 5000) String description,
        @NotNull VendorStatus status,
        Boolean active,
        Boolean acceptingOrders,
        @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal commissionRate,
        // Gap 51: Policies
        @Size(max = 5000) String returnPolicy,
        @Size(max = 5000) String shippingPolicy,
        @Min(0) @Max(365) Integer processingTimeDays,
        Boolean acceptsReturns,
        @Min(0) @Max(365) Integer returnWindowDays,
        @DecimalMin("0.00") BigDecimal freeShippingThreshold,
        // Gap 52: Categories
        @Size(max = 100) String primaryCategory,
        Set<@Size(max = 100) String> specializations
) {
}
