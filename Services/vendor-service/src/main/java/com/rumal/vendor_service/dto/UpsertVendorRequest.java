package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        @Size(max = 500) String description,
        @NotNull VendorStatus status,
        Boolean active
) {
}
