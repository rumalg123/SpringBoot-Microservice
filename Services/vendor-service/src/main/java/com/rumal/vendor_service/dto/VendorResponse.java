package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorStatus;
import com.rumal.vendor_service.entity.VerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
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
        boolean acceptingOrders,
        // Gap 49: Verification
        VerificationStatus verificationStatus,
        boolean verified,
        Instant verifiedAt,
        Instant verificationRequestedAt,
        String verificationNotes,
        String verificationDocumentUrl,
        // Gap 50: Metrics
        BigDecimal averageRating,
        int totalRatings,
        BigDecimal fulfillmentRate,
        BigDecimal disputeRate,
        BigDecimal responseTimeHours,
        int totalOrdersCompleted,
        // Gap 51: Policies
        String returnPolicy,
        String shippingPolicy,
        int processingTimeDays,
        boolean acceptsReturns,
        int returnWindowDays,
        BigDecimal freeShippingThreshold,
        // Gap 52: Categories
        String primaryCategory,
        Set<String> specializations,
        // Existing fields
        boolean deleted,
        Instant deletedAt,
        Instant deletionRequestedAt,
        String deletionRequestReason,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal commissionRate
) {
}
