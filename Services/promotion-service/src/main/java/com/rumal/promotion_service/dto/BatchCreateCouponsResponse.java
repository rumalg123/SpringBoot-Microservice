package com.rumal.promotion_service.dto;

import java.util.List;

public record BatchCreateCouponsResponse(
        int requestedCount,
        int createdCount,
        List<CouponCodeResponse> coupons
) {
}
