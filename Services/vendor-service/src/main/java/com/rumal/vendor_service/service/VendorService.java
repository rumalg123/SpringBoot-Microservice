package com.rumal.vendor_service.service;

import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorAccessMembershipResponse;
import com.rumal.vendor_service.dto.VendorLifecycleAuditResponse;
import com.rumal.vendor_service.dto.VendorDeletionEligibilityResponse;
import com.rumal.vendor_service.dto.VendorOperationalStateResponse;
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
    List<VendorLifecycleAuditResponse> listLifecycleAudit(UUID id);
    VendorDeletionEligibilityResponse getDeletionEligibility(UUID id);
    void softDelete(UUID id);
    void softDelete(UUID id, String reason, String actorSub, String actorRoles);
    VendorResponse requestDelete(UUID id, String reason, String actorSub, String actorRoles);
    void confirmDelete(UUID id, String reason, String actorSub, String actorRoles);
    VendorResponse stopReceivingOrders(UUID id);
    VendorResponse stopReceivingOrders(UUID id, String reason, String actorSub, String actorRoles);
    VendorResponse resumeReceivingOrders(UUID id);
    VendorResponse resumeReceivingOrders(UUID id, String reason, String actorSub, String actorRoles);
    VendorResponse restore(UUID id);
    VendorResponse restore(UUID id, String reason, String actorSub, String actorRoles);
    boolean isSlugAvailable(String slug, UUID excludeId);

    List<VendorUserResponse> listVendorUsers(UUID vendorId);
    VendorUserResponse addVendorUser(UUID vendorId, UpsertVendorUserRequest request);
    VendorUserResponse updateVendorUser(UUID vendorId, UUID membershipId, UpsertVendorUserRequest request);
    void removeVendorUser(UUID vendorId, UUID membershipId);
    List<VendorAccessMembershipResponse> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId);
    VendorOperationalStateResponse getOperationalState(UUID vendorId);
    List<VendorOperationalStateResponse> getOperationalStates(List<UUID> vendorIds);
}
