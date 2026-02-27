package com.rumal.access_service.service;

import com.rumal.access_service.dto.AccessChangeAuditPageResponse;
import com.rumal.access_service.dto.ActiveSessionResponse;
import com.rumal.access_service.dto.ApiKeyResponse;
import com.rumal.access_service.dto.CreateApiKeyRequest;
import com.rumal.access_service.dto.CreateApiKeyResponse;
import com.rumal.access_service.dto.PermissionGroupResponse;
import com.rumal.access_service.dto.PlatformAccessLookupResponse;
import com.rumal.access_service.dto.PlatformStaffAccessResponse;
import com.rumal.access_service.dto.RegisterSessionRequest;
import com.rumal.access_service.dto.UpsertPermissionGroupRequest;
import com.rumal.access_service.dto.UpsertPlatformStaffAccessRequest;
import com.rumal.access_service.dto.UpsertVendorStaffAccessRequest;
import com.rumal.access_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.access_service.dto.VendorStaffAccessResponse;
import com.rumal.access_service.entity.PermissionGroupScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    Page<PlatformStaffAccessResponse> listPlatformStaff(Pageable pageable);
    Page<PlatformStaffAccessResponse> listDeletedPlatformStaff(Pageable pageable);
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

    Page<VendorStaffAccessResponse> listVendorStaff(UUID vendorId, Pageable pageable);
    Page<VendorStaffAccessResponse> listAllVendorStaff(Pageable pageable);
    Page<VendorStaffAccessResponse> listDeletedVendorStaff(Pageable pageable);
    Page<VendorStaffAccessResponse> listDeletedVendorStaff(UUID vendorId, Pageable pageable);
    VendorStaffAccessResponse getVendorStaffById(UUID id);
    VendorStaffAccessResponse getVendorStaffById(UUID id, UUID callerVendorId);
    VendorStaffAccessResponse createVendorStaff(UpsertVendorStaffAccessRequest request);
    VendorStaffAccessResponse createVendorStaff(UpsertVendorStaffAccessRequest request, String actorSub, String actorRoles, String reason);
    VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request);
    VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request, String actorSub, String actorRoles, String reason);
    void softDeleteVendorStaff(UUID id);
    void softDeleteVendorStaff(UUID id, String actorSub, String actorRoles, String reason);
    void softDeleteVendorStaff(UUID id, String actorSub, String actorRoles, String reason, UUID callerVendorId);
    VendorStaffAccessResponse restoreVendorStaff(UUID id);
    VendorStaffAccessResponse restoreVendorStaff(UUID id, String actorSub, String actorRoles, String reason);
    VendorStaffAccessResponse restoreVendorStaff(UUID id, String actorSub, String actorRoles, String reason, UUID callerVendorId);
    List<VendorStaffAccessLookupResponse> listVendorStaffAccessByKeycloakUser(String keycloakUserId);

    // Permission groups
    Page<PermissionGroupResponse> listPermissionGroups(PermissionGroupScope scope, Pageable pageable);
    PermissionGroupResponse getPermissionGroupById(UUID id);
    PermissionGroupResponse createPermissionGroup(UpsertPermissionGroupRequest request);
    PermissionGroupResponse updatePermissionGroup(UUID id, UpsertPermissionGroupRequest request);
    void deletePermissionGroup(UUID id);

    // Session management
    ActiveSessionResponse registerSession(RegisterSessionRequest request);
    Page<ActiveSessionResponse> listSessionsByKeycloakId(String keycloakId, Pageable pageable);
    void revokeSession(UUID sessionId);
    void revokeAllSessions(String keycloakId);

    // API key management
    CreateApiKeyResponse createApiKey(CreateApiKeyRequest request);
    Page<ApiKeyResponse> listApiKeys(String keycloakId, Pageable pageable);
    void deleteApiKey(UUID id);

}
