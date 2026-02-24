package com.rumal.vendor_service.service;

import com.rumal.vendor_service.dto.AdminVerificationActionRequest;
import com.rumal.vendor_service.dto.RequestVerificationRequest;
import com.rumal.vendor_service.dto.UpdateVendorMetricsRequest;
import com.rumal.vendor_service.dto.UpdateVendorSelfServiceRequest;
import com.rumal.vendor_service.dto.UpsertVendorPayoutConfigRequest;
import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorAccessMembershipResponse;
import com.rumal.vendor_service.dto.VendorLifecycleAuditResponse;
import com.rumal.vendor_service.dto.VendorDeletionEligibilityResponse;
import com.rumal.vendor_service.dto.VendorOperationalStateResponse;
import com.rumal.vendor_service.dto.VendorPayoutConfigResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.dto.VendorUserResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface VendorService {
    VendorResponse create(UpsertVendorRequest request);
    VendorResponse update(UUID id, UpsertVendorRequest request);
    VendorResponse getByIdOrSlug(String idOrSlug);
    VendorResponse getAdminById(UUID id);
    List<VendorResponse> listPublicActive();
    List<VendorResponse> listPublicActive(String category);
    Page<VendorResponse> listPublicActive(String category, Pageable pageable);
    List<VendorResponse> listAllNonDeleted();
    Page<VendorResponse> listAllNonDeleted(Pageable pageable);
    List<VendorResponse> listDeleted();
    Page<VendorResponse> listDeleted(Pageable pageable);
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

    VendorResponse getVendorForKeycloakUser(String keycloakUserId, UUID vendorIdHint);
    VendorResponse updateVendorSelfService(String keycloakUserId, UUID vendorIdHint, UpdateVendorSelfServiceRequest request);
    VendorResponse selfServiceStopOrders(String keycloakUserId, UUID vendorIdHint);
    VendorResponse selfServiceResumeOrders(String keycloakUserId, UUID vendorIdHint);
    VendorPayoutConfigResponse getPayoutConfig(String keycloakUserId, UUID vendorIdHint);
    VendorPayoutConfigResponse upsertPayoutConfig(String keycloakUserId, UUID vendorIdHint, UpsertVendorPayoutConfigRequest request);

    // Gap 49: Verification
    VendorResponse requestVerification(String keycloakUserId, UUID vendorIdHint, RequestVerificationRequest request);
    VendorResponse approveVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles);
    VendorResponse rejectVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles);

    // Gap 50: Performance metrics
    VendorResponse updateMetrics(UUID vendorId, UpdateVendorMetricsRequest request);
}
