package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.CouponUsageResponse;
import com.rumal.promotion_service.security.InternalRequestVerifier;
import com.rumal.promotion_service.service.CustomerPromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/promotions/me")
@RequiredArgsConstructor
public class CustomerPromotionController {

    private final CustomerPromotionService customerPromotionService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/coupon-usage")
    public Page<CouponUsageResponse> couponUsage(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub") String userSub,
            @PageableDefault(size = 20, sort = "committedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID customerId = UUID.fromString(userSub);
        return customerPromotionService.getUsageHistory(customerId, pageable);
    }
}
