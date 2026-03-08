package com.rumal.vendor_service.service;

import com.rumal.vendor_service.client.AccessClient;
import com.rumal.vendor_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.vendor_service.entity.Vendor;
import com.rumal.vendor_service.entity.VendorUser;
import com.rumal.vendor_service.entity.VendorUserRole;
import com.rumal.vendor_service.exception.UnauthorizedException;
import com.rumal.vendor_service.repo.VendorUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VendorSelfAccessScopeServiceTests {

    private final AccessClient accessClient = mock(AccessClient.class);
    private final VendorUserRepository vendorUserRepository = mock(VendorUserRepository.class);
    private final VendorSelfAccessScopeService service = new VendorSelfAccessScopeService(accessClient, vendorUserRepository);

    @Test
    void resolveVendorIdForOwnerRejectsVendorAdminWithoutOwnerMembership() {
        UUID vendorId = UUID.randomUUID();

        when(vendorUserRepository.findAccessibleMembershipsByKeycloakUser("ownerless-admin")).thenReturn(List.of(
                membership(vendorId, "ownerless-admin", VendorUserRole.MANAGER)
        ));

        assertThrows(UnauthorizedException.class,
                () -> service.resolveVendorIdForOwner("ownerless-admin", "vendor_admin", vendorId));
    }

    @Test
    void resolveVendorIdForSettingsManagePinsVendorAdminToAccessibleMembership() {
        UUID vendorId = UUID.randomUUID();

        when(vendorUserRepository.findAccessibleMembershipsByKeycloakUser("vendor-admin")).thenReturn(List.of(
                membership(vendorId, "vendor-admin", VendorUserRole.OWNER)
        ));

        UUID resolved = service.resolveVendorIdForSettingsManage("vendor-admin", "vendor_admin", "internal", null);

        assertEquals(vendorId, resolved);
    }

    @Test
    void resolveVendorIdForOrderManageRejectsVendorStaffWithoutPermission() {
        UUID vendorId = UUID.randomUUID();

        when(accessClient.listVendorStaffAccessByKeycloakUser("vendor-staff", "internal")).thenReturn(List.of(
                new VendorStaffAccessLookupResponse(vendorId, true, Set.of("vendor.products.manage"))
        ));

        assertThrows(UnauthorizedException.class,
                () -> service.resolveVendorIdForOrderManage("vendor-staff", "vendor_staff", "internal", vendorId));
    }

    private VendorUser membership(UUID vendorId, String keycloakUserId, VendorUserRole role) {
        return VendorUser.builder()
                .vendor(Vendor.builder().id(vendorId).build())
                .keycloakUserId(keycloakUserId)
                .email(keycloakUserId + "@example.com")
                .role(role)
                .active(true)
                .build();
    }
}
