package com.rumal.promotion_service.service;

import com.rumal.promotion_service.client.AccessClient;
import com.rumal.promotion_service.client.VendorAccessClient;
import com.rumal.promotion_service.dto.PlatformAccessLookupResponse;
import com.rumal.promotion_service.dto.UpsertPromotionRequest;
import com.rumal.promotion_service.dto.VendorAccessMembershipResponse;
import com.rumal.promotion_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.exception.UnauthorizedException;
import com.rumal.promotion_service.exception.ValidationException;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPromotionAccessScopeServiceTest {

    @Mock
    private AccessClient accessClient;

    @Mock
    private VendorAccessClient vendorAccessClient;

    @Mock
    private PromotionCampaignRepository promotionCampaignRepository;

    private AdminPromotionAccessScopeService service;

    @BeforeEach
    void setUp() {
        service = new AdminPromotionAccessScopeService(accessClient, vendorAccessClient, promotionCampaignRepository);
    }

    @Test
    void resolveActorScope_superAdmin_isPlatformPrivilegedWithoutLookups() {
        AdminPromotionAccessScopeService.AdminActorScope scope =
                service.resolveActorScope("user-sub", "super_admin,vendor_staff", "secret");

        assertTrue(scope.superAdmin());
        assertTrue(scope.isPlatformPrivileged());
        assertTrue(scope.vendorPromotionVendorIds().isEmpty());
        verify(accessClient, never()).getPlatformAccessByKeycloakUser(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(vendorAccessClient, never()).listAccessibleVendorsByKeycloakUser(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resolveActorScope_platformStaffWithPermission_grantsPlatformPromotionManagement() {
        when(accessClient.getPlatformAccessByKeycloakUser("staff-sub", "secret"))
                .thenReturn(new PlatformAccessLookupResponse(
                        "staff-sub",
                        true,
                        Set.of(AdminPromotionAccessScopeService.PLATFORM_PROMOTIONS_MANAGE)
                ));

        AdminPromotionAccessScopeService.AdminActorScope scope =
                service.resolveActorScope("staff-sub", "platform_staff", "secret");

        assertFalse(scope.superAdmin());
        assertTrue(scope.platformPromotionsManage());
        assertTrue(scope.isPlatformPrivileged());
        assertTrue(scope.vendorPromotionVendorIds().isEmpty());
    }

    @Test
    void resolveActorScope_vendorAdminAndVendorStaff_mergesAllowedVendorIds() {
        UUID vendorA = UUID.randomUUID();
        UUID vendorB = UUID.randomUUID();
        UUID vendorC = UUID.randomUUID();

        when(vendorAccessClient.listAccessibleVendorsByKeycloakUser("vendor-user", "secret"))
                .thenReturn(List.of(
                        new VendorAccessMembershipResponse(vendorA, "a", "Vendor A", "ADMIN"),
                        new VendorAccessMembershipResponse(vendorB, "b", "Vendor B", "ADMIN")
                ));
        when(accessClient.listVendorStaffAccessByKeycloakUser("vendor-user", "secret"))
                .thenReturn(List.of(
                        new VendorStaffAccessLookupResponse(vendorB, true, Set.of(AdminPromotionAccessScopeService.VENDOR_PROMOTIONS_MANAGE)),
                        new VendorStaffAccessLookupResponse(vendorC, true, Set.of("vendor.orders.manage")),
                        new VendorStaffAccessLookupResponse(UUID.randomUUID(), false, Set.of(AdminPromotionAccessScopeService.VENDOR_PROMOTIONS_MANAGE))
                ));

        AdminPromotionAccessScopeService.AdminActorScope scope =
                service.resolveActorScope("vendor-user", "vendor_admin,vendor_staff", "secret");

        assertFalse(scope.isPlatformPrivileged());
        assertEquals(Set.of(vendorA, vendorB), scope.vendorPromotionVendorIds());
    }

    @Test
    void resolveActorScope_withoutPromotionAccess_throwsUnauthorized() {
        when(accessClient.getPlatformAccessByKeycloakUser("staff-sub", "secret"))
                .thenReturn(new PlatformAccessLookupResponse("staff-sub", true, Set.of("platform.orders.manage")));

        assertThrows(
                UnauthorizedException.class,
                () -> service.resolveActorScope("staff-sub", "platform_staff", "secret")
        );
    }

    @Test
    void resolveScopedVendorFilter_vendorScopedRequiresVendorIdWhenMultipleVendors() {
        AdminPromotionAccessScopeService.AdminActorScope vendorScope =
                new AdminPromotionAccessScopeService.AdminActorScope(false, false, Set.of(UUID.randomUUID(), UUID.randomUUID()));

        assertThrows(ValidationException.class, () -> service.resolveScopedVendorFilter(vendorScope, null));
    }

    @Test
    void scopeCreateRequest_vendorScopedForcesVendorIdAndFundingSource() {
        UUID allowedVendorId = UUID.randomUUID();
        AdminPromotionAccessScopeService.AdminActorScope vendorScope =
                new AdminPromotionAccessScopeService.AdminActorScope(false, false, Set.of(allowedVendorId));

        UpsertPromotionRequest request = sampleRequest(null, PromotionFundingSource.PLATFORM);

        UpsertPromotionRequest scoped = service.scopeCreateRequest(vendorScope, request);

        assertEquals(allowedVendorId, scoped.vendorId());
        assertEquals(PromotionFundingSource.VENDOR, scoped.fundingSource());
        assertEquals(request.name(), scoped.name());
        assertEquals(request.benefitType(), scoped.benefitType());
    }

    @Test
    void assertCanManagePromotion_vendorScopedDeniesPromotionFromAnotherVendor() {
        UUID actorVendorId = UUID.randomUUID();
        UUID promotionId = UUID.randomUUID();
        UUID promotionVendorId = UUID.randomUUID();
        AdminPromotionAccessScopeService.AdminActorScope vendorScope =
                new AdminPromotionAccessScopeService.AdminActorScope(false, false, Set.of(actorVendorId));

        PromotionCampaign promotion = new PromotionCampaign();
        promotion.setId(promotionId);
        promotion.setVendorId(promotionVendorId);
        when(promotionCampaignRepository.findById(promotionId)).thenReturn(Optional.of(promotion));

        assertThrows(UnauthorizedException.class, () -> service.assertCanManagePromotion(vendorScope, promotionId));
    }

    @Test
    void assertCanManagePromotion_vendorScopedAllowsPromotionFromOwnedVendor() {
        UUID actorVendorId = UUID.randomUUID();
        UUID promotionId = UUID.randomUUID();
        AdminPromotionAccessScopeService.AdminActorScope vendorScope =
                new AdminPromotionAccessScopeService.AdminActorScope(false, false, Set.of(actorVendorId));

        PromotionCampaign promotion = new PromotionCampaign();
        promotion.setId(promotionId);
        promotion.setVendorId(actorVendorId);
        when(promotionCampaignRepository.findById(promotionId)).thenReturn(Optional.of(promotion));

        service.assertCanManagePromotion(vendorScope, promotionId);

        verify(promotionCampaignRepository).findById(promotionId);
    }

    private UpsertPromotionRequest sampleRequest(UUID vendorId, PromotionFundingSource fundingSource) {
        return new UpsertPromotionRequest(
                "Flash Sale",
                "Sample promotion",
                vendorId,
                PromotionScopeType.VENDOR,
                Set.of(),
                Set.of(),
                PromotionApplicationLevel.CART,
                PromotionBenefitType.PERCENTAGE_OFF,
                new BigDecimal("10.00"),
                null,
                null,
                null,
                new BigDecimal("50.00"),
                new BigDecimal("25.00"),
                null,
                fundingSource,
                true,
                false,
                true,
                10,
                Instant.parse("2026-02-23T00:00:00Z"),
                Instant.parse("2026-02-28T00:00:00Z")
        );
    }
}
