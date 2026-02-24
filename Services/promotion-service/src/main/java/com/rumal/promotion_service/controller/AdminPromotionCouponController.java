package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.BatchCreateCouponsRequest;
import com.rumal.promotion_service.dto.BatchCreateCouponsResponse;
import com.rumal.promotion_service.dto.CouponCodeResponse;
import com.rumal.promotion_service.dto.CreateCouponCodeRequest;
import com.rumal.promotion_service.security.InternalRequestVerifier;
import com.rumal.promotion_service.service.AdminPromotionAccessScopeService;
import com.rumal.promotion_service.service.CouponCodeAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/promotions/{promotionId}/coupons")
@RequiredArgsConstructor
public class AdminPromotionCouponController {

    private final CouponCodeAdminService couponCodeAdminService;
    private final AdminPromotionAccessScopeService adminPromotionAccessScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public List<CouponCodeResponse> list(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID promotionId
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, promotionId);
        return couponCodeAdminService.listByPromotion(promotionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponCodeResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID promotionId,
            @Valid @RequestBody CreateCouponCodeRequest request
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, promotionId);
        return couponCodeAdminService.create(promotionId, request, userSub);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchCreateCouponsResponse batchCreate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID promotionId,
            @Valid @RequestBody BatchCreateCouponsRequest request
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, promotionId);
        return couponCodeAdminService.batchCreate(promotionId, request, userSub);
    }

    @PatchMapping("/{couponId}/deactivate")
    public CouponCodeResponse deactivate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID promotionId,
            @PathVariable UUID couponId
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, promotionId);
        return couponCodeAdminService.deactivate(promotionId, couponId, userSub);
    }

    @PatchMapping("/{couponId}/activate")
    public CouponCodeResponse activate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID promotionId,
            @PathVariable UUID couponId
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, promotionId);
        return couponCodeAdminService.activate(promotionId, couponId, userSub);
    }

    private AdminPromotionAccessScopeService.AdminActorScope resolveScope(String internalAuth, String userSub, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        return adminPromotionAccessScopeService.resolveActorScope(userSub, userRoles, internalAuth);
    }
}
