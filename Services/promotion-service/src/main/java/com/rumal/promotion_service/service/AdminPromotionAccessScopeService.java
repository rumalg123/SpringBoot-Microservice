package com.rumal.promotion_service.service;

import com.rumal.promotion_service.client.AccessClient;
import com.rumal.promotion_service.client.VendorAccessClient;
import com.rumal.promotion_service.dto.PlatformAccessLookupResponse;
import com.rumal.promotion_service.dto.UpsertPromotionRequest;
import com.rumal.promotion_service.dto.VendorAccessMembershipResponse;
import com.rumal.promotion_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.exception.ResourceNotFoundException;
import com.rumal.promotion_service.exception.UnauthorizedException;
import com.rumal.promotion_service.exception.ValidationException;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminPromotionAccessScopeService {

    public static final String PLATFORM_PROMOTIONS_MANAGE = "platform.promotions.manage";
    public static final String VENDOR_PROMOTIONS_MANAGE = "vendor.promotions.manage";

    private final AccessClient accessClient;
    private final VendorAccessClient vendorAccessClient;
    private final PromotionCampaignRepository promotionCampaignRepository;

    public AdminActorScope resolveActorScope(String userSub, String userRolesHeader, String internalAuth) {
        return resolveScope(userSub, userRolesHeader, internalAuth);
    }

    public UUID resolveScopedVendorFilter(AdminActorScope scope, UUID requestedVendorId) {
        if (scope.isPlatformPrivileged()) {
            return requestedVendorId;
        }
        return resolveVendorIdForVendorScopedActor(scope.vendorPromotionVendorIds(), requestedVendorId);
    }

    public UpsertPromotionRequest scopeCreateRequest(AdminActorScope scope, UpsertPromotionRequest request) {
        if (scope.isPlatformPrivileged()) {
            return request;
        }
        UUID vendorId = resolveVendorIdForVendorScopedActor(scope.vendorPromotionVendorIds(), request.vendorId());
        return copyRequestWithVendorIdAndFunding(request, vendorId, PromotionFundingSource.VENDOR);
    }

    public UpsertPromotionRequest scopeUpdateRequest(AdminActorScope scope, UUID promotionId, UpsertPromotionRequest request) {
        if (scope.isPlatformPrivileged()) {
            return request;
        }
        PromotionCampaign promotion = promotionCampaignRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + promotionId));
        assertVendorScopedActorCanManagePromotion(scope.vendorPromotionVendorIds(), promotion);
        UUID existingVendorId = promotion.getVendorId();
        if (existingVendorId == null) {
            throw new ValidationException("Existing promotion vendorId is missing");
        }
        if (request.vendorId() != null && !existingVendorId.equals(request.vendorId())) {
            throw new UnauthorizedException("Vendor-scoped user cannot reassign promotion to another vendor");
        }
        return copyRequestWithVendorIdAndFunding(request, existingVendorId, PromotionFundingSource.VENDOR);
    }

    public void assertCanManagePromotionOperations(AdminActorScope scope) {
        if (scope.isPlatformPrivileged()) {
            return;
        }
        if (scope.vendorPromotionVendorIds().isEmpty()) {
            throw new UnauthorizedException("Caller does not have promotion management access");
        }
    }

    public void assertCanManagePromotion(AdminActorScope scope, UUID promotionId) {
        assertCanManagePromotionOperations(scope);
        if (scope.isPlatformPrivileged()) {
            return;
        }
        PromotionCampaign promotion = promotionCampaignRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + promotionId));
        assertVendorScopedActorCanManagePromotion(scope.vendorPromotionVendorIds(), promotion);
    }

    public void assertCanApprovePromotions(AdminActorScope scope) {
        if (scope.isPlatformPrivileged()) {
            return;
        }
        throw new UnauthorizedException("Caller does not have promotion approval access");
    }

    private void assertVendorScopedActorCanManagePromotion(Set<UUID> vendorIds, PromotionCampaign promotion) {
        if (promotion.getVendorId() == null || !vendorIds.contains(promotion.getVendorId())) {
            throw new UnauthorizedException("Vendor-scoped user cannot manage promotions of another vendor");
        }
    }

    private UUID resolveVendorIdForVendorScopedActor(Set<UUID> vendorIds, UUID requestedVendorId) {
        if (vendorIds.isEmpty()) {
            throw new UnauthorizedException("No vendor promotion access found");
        }
        if (requestedVendorId != null) {
            if (!vendorIds.contains(requestedVendorId)) {
                throw new UnauthorizedException("Vendor-scoped user cannot use another vendorId");
            }
            return requestedVendorId;
        }
        if (vendorIds.size() == 1) {
            return vendorIds.iterator().next();
        }
        throw new ValidationException("vendorId is required when user has access to multiple vendors");
    }

    private AdminActorScope resolveScope(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return new AdminActorScope(true, true, Set.of());
        }

        if (roles.contains("platform_staff")) {
            PlatformAccessLookupResponse platformAccess = accessClient.getPlatformAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
            boolean promotionsManage = platformAccess.active() && permissions.contains(PLATFORM_PROMOTIONS_MANAGE);
            if (promotionsManage) {
                return new AdminActorScope(false, true, Set.of());
            }
        }

        Set<UUID> vendorIds = new LinkedHashSet<>();
        if (roles.contains("vendor_admin")) {
            List<VendorAccessMembershipResponse> memberships = vendorAccessClient.listAccessibleVendorsByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorAccessMembershipResponse membership : memberships) {
                if (membership != null && membership.vendorId() != null) {
                    vendorIds.add(membership.vendorId());
                }
            }
        }
        if (roles.contains("vendor_staff")) {
            List<VendorStaffAccessLookupResponse> vendorAccessRows = accessClient.listVendorStaffAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorStaffAccessLookupResponse row : vendorAccessRows) {
                if (row == null || row.vendorId() == null || !row.active()) {
                    continue;
                }
                Set<String> perms = row.permissions() == null ? Set.of() : row.permissions();
                if (perms.contains(VENDOR_PROMOTIONS_MANAGE)) {
                    vendorIds.add(row.vendorId());
                }
            }
        }

        if (!vendorIds.isEmpty()) {
            return new AdminActorScope(false, false, Set.copyOf(vendorIds));
        }
        throw new UnauthorizedException("Caller does not have promotion admin access");
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        return userSub.trim();
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : rolesHeader.split(",")) {
            if (role != null && !role.isBlank()) {
                roles.add(role.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(roles);
    }

    private UpsertPromotionRequest copyRequestWithVendorIdAndFunding(
            UpsertPromotionRequest request,
            UUID vendorId,
            PromotionFundingSource fundingSource
    ) {
        return new UpsertPromotionRequest(
                request.name(),
                request.description(),
                vendorId,
                request.scopeType(),
                request.targetProductIds(),
                request.targetCategoryIds(),
                request.applicationLevel(),
                request.benefitType(),
                request.benefitValue(),
                request.minimumOrderAmount(),
                request.maximumDiscountAmount(),
                fundingSource,
                request.stackable(),
                request.exclusive(),
                request.autoApply(),
                request.startsAt(),
                request.endsAt()
        );
    }

    public record AdminActorScope(
            boolean superAdmin,
            boolean platformPromotionsManage,
            Set<UUID> vendorPromotionVendorIds
    ) {
        public boolean isPlatformPrivileged() {
            return superAdmin || platformPromotionsManage;
        }
    }
}
