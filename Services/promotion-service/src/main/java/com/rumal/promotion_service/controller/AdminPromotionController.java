package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.PromotionApprovalDecisionRequest;
import com.rumal.promotion_service.dto.PromotionResponse;
import com.rumal.promotion_service.dto.UpsertPromotionRequest;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.security.InternalRequestVerifier;
import com.rumal.promotion_service.service.AdminPromotionAccessScopeService;
import com.rumal.promotion_service.service.PromotionCampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/promotions")
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionCampaignService promotionCampaignService;
    private final AdminPromotionAccessScopeService adminPromotionAccessScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<PromotionResponse> list(
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
        return promotionCampaignService.list(pageable, q, scopedVendorId, lifecycleStatus, approvalStatus, scopeType, benefitType);
    }

    @GetMapping("/{id}")
    public PromotionResponse get(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, id);
        return promotionCampaignService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody UpsertPromotionRequest request
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotionOperations(scope);
        UpsertPromotionRequest scopedRequest = adminPromotionAccessScopeService.scopeCreateRequest(scope, request);
        return promotionCampaignService.create(scopedRequest, userSub, scope);
    }

    @PutMapping("/{id}")
    public PromotionResponse update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPromotionRequest request
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, id);
        UpsertPromotionRequest scopedRequest = adminPromotionAccessScopeService.scopeUpdateRequest(scope, id, request);
        return promotionCampaignService.update(id, scopedRequest, userSub, scope);
    }

    @PostMapping("/{id}/submit")
    public PromotionResponse submit(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, id);
        return promotionCampaignService.submitForApproval(id, userSub);
    }

    @PostMapping("/{id}/approve")
    public PromotionResponse approve(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) PromotionApprovalDecisionRequest request
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanApprovePromotions(scope);
        return promotionCampaignService.approve(id, userSub, request);
    }

    @PostMapping("/{id}/reject")
    public PromotionResponse reject(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody PromotionApprovalDecisionRequest request
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanApprovePromotions(scope);
        return promotionCampaignService.reject(id, userSub, request);
    }

    @PostMapping("/{id}/activate")
    public PromotionResponse activate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, id);
        return promotionCampaignService.activate(id, userSub);
    }

    @PostMapping("/{id}/pause")
    public PromotionResponse pause(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, id);
        return promotionCampaignService.pause(id, userSub);
    }

    @PostMapping("/{id}/archive")
    public PromotionResponse archive(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        AdminPromotionAccessScopeService.AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminPromotionAccessScopeService.assertCanManagePromotion(scope, id);
        return promotionCampaignService.archive(id, userSub);
    }

    private AdminPromotionAccessScopeService.AdminActorScope resolveScope(String internalAuth, String userSub, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        return adminPromotionAccessScopeService.resolveActorScope(userSub, userRoles, internalAuth);
    }
}
