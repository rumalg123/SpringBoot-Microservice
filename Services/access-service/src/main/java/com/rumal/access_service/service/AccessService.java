package com.rumal.access_service.service;

import com.rumal.access_service.dto.AccessChangeAuditPageResponse;
import com.rumal.access_service.dto.PlatformAccessLookupResponse;
import com.rumal.access_service.dto.PlatformStaffAccessResponse;
import com.rumal.access_service.dto.UpsertPlatformStaffAccessRequest;
import com.rumal.access_service.dto.UpsertVendorStaffAccessRequest;
import com.rumal.access_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.access_service.dto.VendorStaffAccessResponse;

import java.util.List;
import java.util.UUID;

public interface AccessService {
    AccessChangeAuditPageResponse listAccessAudit(
            String targetType,
            UUID targetId,
            UUID vendorId,
            String action,
            String actorQuery,
            String from,
            String to,
            Integer page,
            Integer size,
            Integer limit
    );

    List<PlatformStaffAccessResponse> listPlatformStaff();
    List<PlatformStaffAccessResponse> listDeletedPlatformStaff();
    PlatformStaffAccessResponse getPlatformStaffById(UUID id);
    PlatformStaffAccessResponse createPlatformStaff(UpsertPlatformStaffAccessRequest request);
    PlatformStaffAccessResponse createPlatformStaff(UpsertPlatformStaffAccessRequest request, String actorSub, String actorRoles, String reason);
    PlatformStaffAccessResponse updatePlatformStaff(UUID id, UpsertPlatformStaffAccessRequest request);
    PlatformStaffAccessResponse updatePlatformStaff(UUID id, UpsertPlatformStaffAccessRequest request, String actorSub, String actorRoles, String reason);
    void softDeletePlatformStaff(UUID id);
    void softDeletePlatformStaff(UUID id, String actorSub, String actorRoles, String reason);
    PlatformStaffAccessResponse restorePlatformStaff(UUID id);
    PlatformStaffAccessResponse restorePlatformStaff(UUID id, String actorSub, String actorRoles, String reason);
    PlatformAccessLookupResponse getPlatformAccessByKeycloakUser(String keycloakUserId);

    List<VendorStaffAccessResponse> listVendorStaff(UUID vendorId);
    List<VendorStaffAccessResponse> listAllVendorStaff();
    List<VendorStaffAccessResponse> listDeletedVendorStaff();
    VendorStaffAccessResponse getVendorStaffById(UUID id);
    VendorStaffAccessResponse createVendorStaff(UpsertVendorStaffAccessRequest request);
    VendorStaffAccessResponse createVendorStaff(UpsertVendorStaffAccessRequest request, String actorSub, String actorRoles, String reason);
    VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request);
    VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request, String actorSub, String actorRoles, String reason);
    void softDeleteVendorStaff(UUID id);
    void softDeleteVendorStaff(UUID id, String actorSub, String actorRoles, String reason);
    VendorStaffAccessResponse restoreVendorStaff(UUID id);
    VendorStaffAccessResponse restoreVendorStaff(UUID id, String actorSub, String actorRoles, String reason);
    List<VendorStaffAccessLookupResponse> listVendorStaffAccessByKeycloakUser(String keycloakUserId);
}
