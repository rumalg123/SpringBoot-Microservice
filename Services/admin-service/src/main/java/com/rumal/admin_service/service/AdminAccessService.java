package com.rumal.admin_service.service;

import com.rumal.admin_service.auth.KeycloakVendorAdminManagementService;
import com.rumal.admin_service.client.AccessClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAccessService {

    private final AccessClient accessClient;
    private final KeycloakVendorAdminManagementService keycloakVendorAdminManagementService;

    public Map<String, Object> getPlatformAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        return accessClient.getPlatformAccessByKeycloakUser(keycloakUserId, internalAuth);
    }

    public List<Map<String, Object>> listVendorStaffAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        return accessClient.listVendorStaffAccessByKeycloakUser(keycloakUserId, internalAuth);
    }

    public List<Map<String, Object>> listPlatformStaff(String internalAuth) {
        return accessClient.listPlatformStaff(internalAuth);
    }

    public List<Map<String, Object>> listDeletedPlatformStaff(String internalAuth) {
        return accessClient.listDeletedPlatformStaff(internalAuth);
    }

    public Map<String, Object> getPlatformStaffById(UUID id, String internalAuth) {
        return accessClient.getPlatformStaffById(id, internalAuth);
    }

    public Map<String, Object> createPlatformStaff(Map<String, Object> request, String internalAuth) {
        return accessClient.createPlatformStaff(request, internalAuth);
    }

    public Map<String, Object> createPlatformStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return accessClient.createPlatformStaff(request, internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return updatePlatformStaff(id, request, internalAuth, null, null, null);
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        Map<String, Object> updated = accessClient.updatePlatformStaff(id, request, internalAuth, userSub, userRoles, actionReason);
        if (!isTruthy(updated.get("active"))) {
            revokeKeycloakSessions(extractString(updated.get("keycloakUserId")));
        }
        return updated;
    }

    public void deletePlatformStaff(UUID id, String internalAuth) {
        deletePlatformStaff(id, internalAuth, null, null, null);
    }

    public void deletePlatformStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        Map<String, Object> existing = accessClient.getPlatformStaffById(id, internalAuth);
        accessClient.deletePlatformStaff(id, internalAuth, userSub, userRoles, actionReason);
        revokeKeycloakSessions(extractString(existing.get("keycloakUserId")));
    }

    public Map<String, Object> restorePlatformStaff(UUID id, String internalAuth) {
        return restorePlatformStaff(id, internalAuth, null, null, null);
    }

    public Map<String, Object> restorePlatformStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        return accessClient.restorePlatformStaff(id, internalAuth, userSub, userRoles, actionReason);
    }

    public List<Map<String, Object>> listVendorStaff(UUID vendorId, String internalAuth) {
        return accessClient.listVendorStaff(vendorId, internalAuth);
    }

    public List<Map<String, Object>> listDeletedVendorStaff(String internalAuth) {
        return accessClient.listDeletedVendorStaff(internalAuth);
    }

    public Map<String, Object> listAccessAudit(
            String targetType,
            UUID targetId,
            UUID vendorId,
            String action,
            String actorQuery,
            String from,
            String to,
            Integer page,
            Integer size,
            Integer limit,
            String internalAuth
    ) {
        return accessClient.listAccessAudit(targetType, targetId, vendorId, action, actorQuery, from, to, page, size, limit, internalAuth);
    }

    public Map<String, Object> getVendorStaffById(UUID id, String internalAuth) {
        return accessClient.getVendorStaffById(id, internalAuth);
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth) {
        return createVendorStaff(request, internalAuth, null, null, null);
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return accessClient.createVendorStaff(request, internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return updateVendorStaff(id, request, internalAuth, null, null, null);
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        Map<String, Object> updated = accessClient.updateVendorStaff(id, request, internalAuth, userSub, userRoles, actionReason);
        if (!isTruthy(updated.get("active"))) {
            revokeKeycloakSessions(extractString(updated.get("keycloakUserId")));
        }
        return updated;
    }

    public void deleteVendorStaff(UUID id, String internalAuth) {
        deleteVendorStaff(id, internalAuth, null, null, null);
    }

    public void deleteVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        Map<String, Object> existing = accessClient.getVendorStaffById(id, internalAuth);
        accessClient.deleteVendorStaff(id, internalAuth, userSub, userRoles, actionReason);
        revokeKeycloakSessions(extractString(existing.get("keycloakUserId")));
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth) {
        return restoreVendorStaff(id, internalAuth, null, null, null);
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        return accessClient.restoreVendorStaff(id, internalAuth, userSub, userRoles, actionReason);
    }

    private void revokeKeycloakSessions(String keycloakUserId) {
        if (!StringUtils.hasText(keycloakUserId)) {
            return;
        }
        keycloakVendorAdminManagementService.logoutUserSessions(keycloakUserId);
    }

    private String extractString(Object value) {
        if (value == null) {
            return null;
        }
        String raw = String.valueOf(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
