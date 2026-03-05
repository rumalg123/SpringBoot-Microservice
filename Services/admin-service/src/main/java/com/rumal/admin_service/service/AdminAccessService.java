package com.rumal.admin_service.service;

import com.rumal.admin_service.auth.KeycloakVendorAdminManagementService;
import com.rumal.admin_service.client.AccessClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        return accessClient.createPlatformStaff(prepareStaffUpsertPayload(request), internalAuth);
    }

    public Map<String, Object> createPlatformStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return accessClient.createPlatformStaff(prepareStaffUpsertPayload(request), internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return updatePlatformStaff(id, request, internalAuth, null, null, null);
    }

    public Map<String, Object> updatePlatformStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        Map<String, Object> updated = accessClient.updatePlatformStaff(
                id,
                prepareStaffUpsertPayload(request),
                internalAuth,
                userSub,
                userRoles,
                actionReason
        );
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
        return listVendorStaff(vendorId, internalAuth, null, null);
    }

    public List<Map<String, Object>> listVendorStaff(UUID vendorId, String internalAuth, String userRoles, UUID callerVendorId) {
        return accessClient.listVendorStaff(vendorId, internalAuth, userRoles, callerVendorId);
    }

    public List<Map<String, Object>> listDeletedVendorStaff(String internalAuth) {
        return listDeletedVendorStaff(internalAuth, null, null);
    }

    public List<Map<String, Object>> listDeletedVendorStaff(String internalAuth, String userRoles, UUID callerVendorId) {
        return accessClient.listDeletedVendorStaff(internalAuth, userRoles, callerVendorId);
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
        return getVendorStaffById(id, internalAuth, null, null);
    }

    public Map<String, Object> getVendorStaffById(UUID id, String internalAuth, String userRoles, UUID callerVendorId) {
        return accessClient.getVendorStaffById(id, internalAuth, userRoles, callerVendorId);
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth) {
        return createVendorStaff(request, internalAuth, null, null, null);
    }

    public Map<String, Object> createVendorStaff(Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        return accessClient.createVendorStaff(prepareStaffUpsertPayload(request), internalAuth, userSub, userRoles, actionReason);
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth) {
        return updateVendorStaff(id, request, internalAuth, null, null, null);
    }

    public Map<String, Object> updateVendorStaff(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String actionReason) {
        Map<String, Object> updated = accessClient.updateVendorStaff(
                id,
                prepareStaffUpsertPayload(request),
                internalAuth,
                userSub,
                userRoles,
                actionReason
        );
        if (!isTruthy(updated.get("active"))) {
            revokeKeycloakSessions(extractString(updated.get("keycloakUserId")));
        }
        return updated;
    }

    public void deleteVendorStaff(UUID id, String internalAuth) {
        deleteVendorStaff(id, internalAuth, null, null, null);
    }

    public void deleteVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        deleteVendorStaff(id, internalAuth, userSub, userRoles, actionReason, null);
    }

    public void deleteVendorStaff(
            UUID id,
            String internalAuth,
            String userSub,
            String userRoles,
            String actionReason,
            UUID callerVendorId
    ) {
        Map<String, Object> existing = accessClient.getVendorStaffById(id, internalAuth, userRoles, callerVendorId);
        accessClient.deleteVendorStaff(id, internalAuth, userSub, userRoles, actionReason, callerVendorId);
        revokeKeycloakSessions(extractString(existing.get("keycloakUserId")));
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth) {
        return restoreVendorStaff(id, internalAuth, null, null, null);
    }

    public Map<String, Object> restoreVendorStaff(UUID id, String internalAuth, String userSub, String userRoles, String actionReason) {
        return restoreVendorStaff(id, internalAuth, userSub, userRoles, actionReason, null);
    }

    public Map<String, Object> restoreVendorStaff(
            UUID id,
            String internalAuth,
            String userSub,
            String userRoles,
            String actionReason,
            UUID callerVendorId
    ) {
        return accessClient.restoreVendorStaff(id, internalAuth, userSub, userRoles, actionReason, callerVendorId);
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

    private Map<String, Object> prepareStaffUpsertPayload(Map<String, Object> request) {
        Map<String, Object> payload = new LinkedHashMap<>(request == null ? Map.of() : request);
        String email = normalizeRequiredEmail(payload.get("email"));
        String requestedKeycloakUserId = extractString(payload.get("keycloakUserId"));
        String resolvedKeycloakUserId = keycloakVendorAdminManagementService.resolveUserIdByEmail(email);
        if (StringUtils.hasText(requestedKeycloakUserId)
                && !requestedKeycloakUserId.equalsIgnoreCase(resolvedKeycloakUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provided keycloakUserId does not match the user resolved from email"
            );
        }
        payload.put("email", email);
        payload.put("keycloakUserId", resolvedKeycloakUserId);
        return payload;
    }

    private String normalizeRequiredEmail(Object emailRaw) {
        String email = extractString(emailRaw);
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        return email.toLowerCase(Locale.ROOT);
    }
}
