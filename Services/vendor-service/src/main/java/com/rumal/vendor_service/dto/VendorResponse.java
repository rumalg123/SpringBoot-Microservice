package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorStatus;

import java.time.Instant;
import java.util.UUID;

public record VendorResponse(
        UUID id,
        String name,
        String slug,
        String contactEmail,
        String supportEmail,
        String contactPhone,
        String contactPersonName,
        String logoImage,
        String bannerImage,
        String websiteUrl,
        String description,
        VendorStatus status,
        boolean active,
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
