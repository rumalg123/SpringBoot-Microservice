package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.PromotionAnalyticsResponse;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.security.InternalRequestVerifier;
import com.rumal.promotion_service.service.AdminPromotionAccessScopeService;
import com.rumal.promotion_service.service.PromotionAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionAnalyticsController {

    private final PromotionAnalyticsService promotionAnalyticsService;
    private final AdminPromotionAccessScopeService adminPromotionAccessScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/analytics")
    public Page<PromotionAnalyticsResponse> listAnalytics(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) PromotionLifecycleStatus lifecycleStatus,
            @RequestParam(required = false) PromotionApprovalStatus approvalStatus,
            @RequestParam(required = false) PromotionScopeType scopeType,
            @RequestParam(required = false) PromotionBenefitType benefitType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotionOperations(scope);
        UUID scopedVendorId = adminPromotionAccessScopeService.resolveScopedVendorFilter(scope, vendorId);
        return promotionAnalyticsService.list(pageable, q, scopedVendorId, lifecycleStatus, approvalStatus, scopeType, benefitType);
    }

    @GetMapping("/{promotionId}/analytics")
    public PromotionAnalyticsResponse getAnalytics(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID promotionId
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, promotionId);
        return promotionAnalyticsService.get(promotionId);
    }

    private AdminPromotionAccessScopeService.AdminActorScope resolveScope(String internalAuth, String userSub, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        return adminPromotionAccessScopeService.resolveActorScope(userSub, userRoles, internalAuth);
    }
}
