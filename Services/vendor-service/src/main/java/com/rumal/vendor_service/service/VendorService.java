package com.rumal.vendor_service.service;

import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorAccessMembershipResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.dto.VendorUserResponse;

import java.util.List;
import java.util.UUID;

public interface VendorService {
    VendorResponse create(UpsertVendorRequest request);
    VendorResponse update(UUID id, UpsertVendorRequest request);
    VendorResponse getByIdOrSlug(String idOrSlug);
    VendorResponse getAdminById(UUID id);
    List<VendorResponse> listPublicActive();
    List<VendorResponse> listAllNonDeleted();
    List<VendorResponse> listDeleted();
    void softDelete(UUID id);
    VendorResponse restore(UUID id);
    boolean isSlugAvailable(String slug, UUID excludeId);

    List<VendorUserResponse> listVendorUsers(UUID vendorId);
    VendorUserResponse addVendorUser(UUID vendorId, UpsertVendorUserRequest request);
    VendorUserResponse updateVendorUser(UUID vendorId, UUID membershipId, UpsertVendorUserRequest request);
    void removeVendorUser(UUID vendorId, UUID membershipId);
    List<VendorAccessMembershipResponse> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId);
}
