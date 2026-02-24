package com.rumal.vendor_service.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateVendorMetricsRequest(
        @DecimalMin("0.00") @DecimalMax("5.00") BigDecimal averageRating,
        @Min(0) Integer totalRatings,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal fulfillmentRate,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal disputeRate,
        @DecimalMin("0.00") BigDecimal responseTimeHours,
        @Min(0) Integer totalOrdersCompleted
) {
}
