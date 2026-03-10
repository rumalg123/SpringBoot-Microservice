package com.rumal.admin_service.service;

import com.rumal.admin_service.auth.KeycloakVendorAdminManagementService;
import com.rumal.admin_service.client.AccessClient;
import com.rumal.admin_service.client.VendorClient;
import com.rumal.admin_service.dto.VendorAdminOnboardRequest;
import com.rumal.admin_service.dto.VendorAdminOnboardResponse;
import com.rumal.admin_service.exception.DownstreamHttpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminVendorService {

    private static final String KEYCLOAK_USER_ID_FIELD = "keycloakUserId";
    private static final String VENDOR_ID_FIELD = "vendorId";
    private static final String ACTIVE_FIELD = "active";
    private static final String EMAIL_FIELD = "email";
    private static final String DISPLAY_NAME_FIELD = "displayName";
    private static final String MEMBERSHIP_ID_FIELD_NAME = "vendor membership id";
    private static final String MANAGER_ROLE = "MANAGER";
    private static final String OWNER_ROLE = "OWNER";
    private static final String VENDOR_ADMIN_ROLE = "vendor_admin";

    private final VendorClient vendorClient;
    private final AccessClient accessClient;
    private final KeycloakVendorAdminManagementService keycloakVendorAdminManagementService;

    public List<Map<String, Object>> listAll(String internalAuth) {
        return listAll(internalAuth, null, null);
    }

    public List<Map<String, Object>> listAll(String internalAuth, String userSub, String userRoles) {
        return vendorClient.listAll(internalAuth, userSub, userRoles);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth) {
        return listDeleted(internalAuth, null, null);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth, String userSub, String userRoles) {
        return vendorClient.listDeleted(internalAuth, userSub, userRoles);
    }

    public Map<String, Object> listLifecycleAudit(UUID id, String internalAuth) {
        return listLifecycleAudit(id, internalAuth, null, null);
    }

    public Map<String, Object> listLifecycleAudit(UUID id, String internalAuth, String userSub, String userRoles) {
        return vendorClient.listLifecycleAudit(id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> getById(UUID id, String internalAuth) {
        return getById(id, internalAuth, null, null);
    }

    public Map<String, Object> getById(UUID id, String internalAuth, String userSub, String userRoles) {
        return vendorClient.getById(id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> getDeletionEligibility(UUID id, String internalAuth) {
        return getDeletionEligibility(id, internalAuth, null, null);
    }

    public Map<String, Object> getDeletionEligibility(UUID id, String internalAuth, String userSub, String userRoles) {
        return vendorClient.getDeletionEligibility(id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth) {
        return vendorClient.create(request, internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return vendorClient.create(request, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth) {
        return vendorClient.update(id, request, internalAuth);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        Map<String, Object> before = vendorClient.getById(id, internalAuth, userSub, userRoles);
        Map<String, Object> updated = vendorClient.update(id, request, internalAuth, userSub, userRoles);
        if (isVendorSecurityOperational(before) && !isVendorSecurityOperational(updated)) {
            revokeSessionsForVendorPrincipals(id, internalAuth, userSub, userRoles);
        }
        return updated;
    }

    public void delete(UUID id, String internalAuth) {
        vendorClient.delete(id, internalAuth);
    }

    public void delete(UUID id, String internalAuth, String userSub, String userRoles) {
        vendorClient.delete(id, internalAuth, userSub, userRoles);
        revokeSessionsForVendorPrincipals(id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> requestDelete(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return requestDelete(id, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> requestDelete(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return vendorClient.requestDelete(id, request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public void confirmDelete(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        confirmDelete(id, request, internalAuth, userSub, userRoles, null);
    }

    public void confirmDelete(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        vendorClient.confirmDelete(id, request, internalAuth, userSub, userRoles, idempotencyKey);
        revokeSessionsForVendorPrincipals(id, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> stopReceivingOrders(UUID id, String internalAuth) {
        return vendorClient.stopReceivingOrders(id, internalAuth);
    }

    public Map<String, Object> stopReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return stopReceivingOrders(id, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> stopReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return vendorClient.stopReceivingOrders(id, request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, String internalAuth) {
        return vendorClient.resumeReceivingOrders(id, internalAuth);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return resumeReceivingOrders(id, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> resumeReceivingOrders(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return vendorClient.resumeReceivingOrders(id, request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> approveVerification(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return vendorClient.approveVerification(id, request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> rejectVerification(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return vendorClient.rejectVerification(id, request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        return vendorClient.restore(id, internalAuth);
    }

    public Map<String, Object> restore(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return restore(id, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> restore(UUID id, Map<String, Object> request, String internalAuth, String userSub, String userRoles, String idempotencyKey) {
        return vendorClient.restore(id, request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public List<Map<String, Object>> listVendorUsers(UUID vendorId, String internalAuth) {
        return listVendorUsers(vendorId, internalAuth, null, null);
    }

    public List<Map<String, Object>> listVendorUsers(UUID vendorId, String internalAuth, String userSub, String userRoles) {
        return vendorClient.listVendorUsers(vendorId, internalAuth, userSub, userRoles);
    }

    public List<Map<String, Object>> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId, String internalAuth) {
        return vendorClient.listAccessibleVendorMembershipsByKeycloakUser(keycloakUserId, internalAuth);
    }

    public Set<String> listLinkedKeycloakUserIdsForVendorAdmin(String keycloakUserId, String internalAuth) {
        String normalizedUserSub = normalizeRequired(keycloakUserId, KEYCLOAK_USER_ID_FIELD, 120);
        Set<UUID> vendorIds = resolveLinkedVendorIds(normalizedUserSub, internalAuth);
        if (vendorIds.isEmpty()) {
            return Set.of();
        }

        Set<String> linkedKeycloakUserIds = new LinkedHashSet<>();
        for (UUID vendorId : vendorIds) {
            linkedKeycloakUserIds.addAll(collectActiveVendorUserIds(vendorId, internalAuth));
            linkedKeycloakUserIds.addAll(collectActiveVendorStaffIds(vendorId, internalAuth));
        }

        return Set.copyOf(linkedKeycloakUserIds);
    }

    public int reconcileVendorStaffMemberships(UUID vendorId, String internalAuth, String userSub, String userRoles) {
        List<Map<String, Object>> rows = accessClient.listVendorStaff(vendorId, internalAuth, userRoles, vendorId);
        for (Map<String, Object> row : rows) {
            syncVendorStaffMembershipTransition(null, row, internalAuth, userSub, userRoles);
        }
        return rows.size();
    }

    /**
     * Keeps vendor membership (vendor_users) in sync with vendor_staff_access rows.
     * This avoids tenant-access drift where vendor staff permissions exist but /vendors/me cannot resolve membership.
     */
    public void syncVendorStaffMembershipTransition(
            Map<String, Object> beforeRow,
            Map<String, Object> afterRow,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
        if (beforeRow == null && afterRow == null) {
            return;
        }

        if (beforeRow != null && afterRow == null) {
            Map<String, Object> deactivate = new LinkedHashMap<>(beforeRow);
            deactivate.put(ACTIVE_FIELD, false);
            syncVendorStaffMembership(deactivate, internalAuth, userSub, userRoles);
            return;
        }

        if (beforeRow != null && !isSameVendorPrincipal(beforeRow, afterRow)) {
            Map<String, Object> deactivateOld = new LinkedHashMap<>(beforeRow);
            deactivateOld.put(ACTIVE_FIELD, false);
            syncVendorStaffMembership(deactivateOld, internalAuth, userSub, userRoles);
        }

        syncVendorStaffMembership(afterRow, internalAuth, userSub, userRoles);
    }

    public Map<String, Object> addVendorUser(UUID vendorId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return addVendorUser(vendorId, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> addVendorUser(
            UUID vendorId,
            Map<String, Object> request,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        return vendorClient.addVendorUser(vendorId, request, internalAuth, userSub, userRoles, idempotencyKey);
    }

    public Map<String, Object> updateVendorUser(UUID vendorId, UUID membershipId, Map<String, Object> request, String internalAuth, String userSub, String userRoles) {
        return updateVendorUser(vendorId, membershipId, request, internalAuth, userSub, userRoles, null);
    }

    public Map<String, Object> updateVendorUser(
            UUID vendorId,
            UUID membershipId,
            Map<String, Object> request,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        Map<String, Object> updated = vendorClient.updateVendorUser(vendorId, membershipId, request, internalAuth, userSub, userRoles, idempotencyKey);
        if (!isTruthy(updated.get(ACTIVE_FIELD))) {
            String keycloakUserId = stringOrNull(updated.get(KEYCLOAK_USER_ID_FIELD));
            if (StringUtils.hasText(keycloakUserId)) {
                keycloakVendorAdminManagementService.logoutUserSessions(keycloakUserId);
            }
        }
        return updated;
    }

    public void removeVendorUser(UUID vendorId, UUID membershipId, String internalAuth, String userSub, String userRoles) {
        removeVendorUser(vendorId, membershipId, internalAuth, userSub, userRoles, null);
    }

    public void removeVendorUser(
            UUID vendorId,
            UUID membershipId,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        String keycloakUserId = null;
        for (Map<String, Object> row : vendorClient.listVendorUsers(vendorId, internalAuth, userSub, userRoles)) {
            UUID rowId = tryParseUuid(row.get("id"));
            if (membershipId.equals(rowId)) {
                keycloakUserId = stringOrNull(row.get(KEYCLOAK_USER_ID_FIELD));
                break;
            }
        }
        vendorClient.removeVendorUser(vendorId, membershipId, internalAuth, userSub, userRoles, idempotencyKey);
        if (StringUtils.hasText(keycloakUserId)) {
            keycloakVendorAdminManagementService.logoutUserSessions(keycloakUserId);
        }
    }

    public VendorAdminOnboardResponse onboardVendorAdmin(
            UUID vendorId,
            VendorAdminOnboardRequest request,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        // Fail fast before touching Keycloak if vendor does not exist.
        vendorClient.getById(vendorId, internalAuth, userSub, userRoles);

        boolean createIfMissing = request.createIfMissing() == null || request.createIfMissing();
        var managedUser = keycloakVendorAdminManagementService.ensureVendorAdminUser(
                request.keycloakUserId(),
                request.email(),
                request.firstName(),
                request.lastName(),
                createIfMissing
        );

        String role = normalizeVendorUserRole(request.vendorUserRole());
        String displayName = resolveDisplayName(request.displayName(), managedUser.firstName(), managedUser.lastName(), managedUser.email());

        Map<String, Object> membershipRequest = Map.of(
                KEYCLOAK_USER_ID_FIELD, managedUser.id(),
                EMAIL_FIELD, managedUser.email(),
                DISPLAY_NAME_FIELD, displayName,
                "role", role,
                ACTIVE_FIELD, true
        );

        Map<String, Object> membership;
        try {
            membership = upsertVendorMembership(vendorId, managedUser.id(), membershipRequest, internalAuth, userSub, userRoles, idempotencyKey);
        } catch (RuntimeException ex) {
            Map<String, Object> recoveredMembership = tryFindVendorMembership(vendorId, managedUser.id(), internalAuth, userSub, userRoles);
            if (!recoveredMembership.isEmpty()) {
                log.warn("Recovered vendor onboarding for user {} after downstream failure: {}", managedUser.id(), ex.getMessage());
                membership = recoveredMembership;
            } else {
                compensateVendorAdminOnboarding(managedUser, ex);
                throw ex;
            }
        }

        return new VendorAdminOnboardResponse(
                vendorId,
                managedUser.created(),
                managedUser.actionEmailSent(),
                managedUser.id(),
                managedUser.email(),
                managedUser.firstName(),
                managedUser.lastName(),
                membership
        );
    }

    private void compensateVendorAdminOnboarding(
            KeycloakVendorAdminManagementService.KeycloakManagedUser managedUser,
            RuntimeException failure
    ) {
        if (!managedUser.created()) {
            return;
        }
        try {
            keycloakVendorAdminManagementService.deleteUser(managedUser.id());
        } catch (RuntimeException cleanupEx) {
            log.error(
                    "Failed to cleanup orphaned Keycloak vendor admin {} after downstream failure: {}",
                    managedUser.id(),
                    cleanupEx.getMessage()
            );
            return;
        }
        log.warn("Compensated orphaned Keycloak vendor admin {} after downstream failure: {}", managedUser.id(), failure.getMessage());
    }

    private Map<String, Object> tryFindVendorMembership(
            UUID vendorId,
            String keycloakUserId,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
        try {
            List<Map<String, Object>> memberships = vendorClient.listVendorUsers(vendorId, internalAuth, userSub, userRoles);
            return findMembershipByKeycloakUserId(memberships, keycloakUserId);
        } catch (RuntimeException ex) {
            log.warn("Failed to verify vendor membership recovery for user {}: {}", keycloakUserId, ex.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> upsertVendorMembership(
            UUID vendorId,
            String keycloakUserId,
            Map<String, Object> membershipRequest,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        List<Map<String, Object>> memberships = vendorClient.listVendorUsers(vendorId, internalAuth, userSub, userRoles);
        Map<String, Object> existing = findMembershipByKeycloakUserId(memberships, keycloakUserId);

        if (existing.isEmpty()) {
            try {
                return vendorClient.addVendorUser(vendorId, membershipRequest, internalAuth, userSub, userRoles, idempotencyKey);
            } catch (DownstreamHttpException ex) {
                if (ex.getStatusCode().value() == 409) {
                    memberships = vendorClient.listVendorUsers(vendorId, internalAuth, userSub, userRoles);
                    existing = findMembershipByKeycloakUserId(memberships, keycloakUserId);
                    if (!existing.isEmpty()) {
                        UUID membershipId = parseUuid(existing.get("id"), MEMBERSHIP_ID_FIELD_NAME);
                        return vendorClient.updateVendorUser(vendorId, membershipId, membershipRequest, internalAuth, userSub, userRoles, idempotencyKey);
                    }
                }
                throw ex;
            }
        }

        UUID membershipId = parseUuid(existing.get("id"), MEMBERSHIP_ID_FIELD_NAME);
        return vendorClient.updateVendorUser(vendorId, membershipId, membershipRequest, internalAuth, userSub, userRoles, idempotencyKey);
    }

    private void syncVendorStaffMembership(
            Map<String, Object> vendorStaffRow,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
        UUID vendorId = parseUuid(vendorStaffRow.get(VENDOR_ID_FIELD), VENDOR_ID_FIELD);
        String keycloakUserId = stringOrNull(vendorStaffRow.get(KEYCLOAK_USER_ID_FIELD));
        if (!StringUtils.hasText(keycloakUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Access service response is missing vendor staff " + KEYCLOAK_USER_ID_FIELD);
        }
        boolean desiredActive = isTruthy(vendorStaffRow.get(ACTIVE_FIELD));
        String incomingEmail = stringOrNull(vendorStaffRow.get(EMAIL_FIELD));
        String incomingDisplayName = stringOrNull(vendorStaffRow.get(DISPLAY_NAME_FIELD));
        VendorStaffMembershipSyncContext syncContext = new VendorStaffMembershipSyncContext(
                vendorId,
                keycloakUserId,
                desiredActive,
                incomingEmail,
                incomingDisplayName,
                internalAuth,
                userSub,
                userRoles
        );

        List<Map<String, Object>> memberships = vendorClient.listVendorUsers(vendorId, internalAuth, userSub, userRoles);
        Map<String, Object> existing = findMembershipByKeycloakUserId(memberships, keycloakUserId);
        Map<String, Object> ensuredExisting = ensureVendorStaffMembership(existing, syncContext);
        if (ensuredExisting.isEmpty()) {
            return;
        }
        if (isOwnerMembership(ensuredExisting)) {
            return;
        }

        String effectiveEmail = StringUtils.hasText(syncContext.incomingEmail())
                ? syncContext.incomingEmail()
                : stringOrNull(ensuredExisting.get(EMAIL_FIELD));
        if (!StringUtils.hasText(effectiveEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vendor membership email cannot be resolved for vendor staff sync");
        }
        String effectiveDisplayName = StringUtils.hasText(syncContext.incomingDisplayName())
                ? syncContext.incomingDisplayName()
                : stringOrNull(ensuredExisting.get(DISPLAY_NAME_FIELD));
        UUID membershipId = parseUuid(ensuredExisting.get("id"), MEMBERSHIP_ID_FIELD_NAME);
        Map<String, Object> payload = buildVendorManagerMembershipPayload(
                syncContext.keycloakUserId(),
                effectiveEmail,
                effectiveDisplayName,
                syncContext.desiredActive()
        );
        vendorClient.updateVendorUser(
                syncContext.vendorId(),
                membershipId,
                payload,
                syncContext.internalAuth(),
                syncContext.userSub(),
                syncContext.userRoles()
        );
    }

    private Map<String, Object> buildVendorManagerMembershipPayload(
            String keycloakUserId,
            String email,
            String displayName,
            boolean active
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(KEYCLOAK_USER_ID_FIELD, keycloakUserId);
        payload.put(EMAIL_FIELD, email);
        payload.put(DISPLAY_NAME_FIELD, displayName);
        payload.put("role", MANAGER_ROLE);
        payload.put(ACTIVE_FIELD, active);
        return payload;
    }

    private Map<String, Object> findMembershipByKeycloakUserId(List<Map<String, Object>> memberships, String keycloakUserId) {
        if (memberships == null || memberships.isEmpty()) {
            return Map.of();
        }
        for (Map<String, Object> membership : memberships) {
            if (membership == null) {
                continue;
            }
            if (keycloakUserId.equalsIgnoreCase(stringValue(membership.get(KEYCLOAK_USER_ID_FIELD)).trim())) {
                return membership;
            }
        }
        return Map.of();
    }

    private UUID parseUuid(Object value, String fieldName) {
        String raw = stringValue(value);
        if (!StringUtils.hasText(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vendor service response is missing " + fieldName);
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Vendor service returned invalid " + fieldName);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringOrNull(Object value) {
        String raw = stringValue(value).trim();
        return raw.isEmpty() ? null : raw;
    }

    private UUID tryParseUuid(Object raw) {
        String value = stringOrNull(raw);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isSameVendorPrincipal(Map<String, Object> left, Map<String, Object> right) {
        UUID leftVendorId = tryParseUuid(left.get(VENDOR_ID_FIELD));
        UUID rightVendorId = tryParseUuid(right.get(VENDOR_ID_FIELD));
        String leftUserId = stringOrNull(left.get(KEYCLOAK_USER_ID_FIELD));
        String rightUserId = stringOrNull(right.get(KEYCLOAK_USER_ID_FIELD));
        if (leftVendorId == null || rightVendorId == null) {
            return false;
        }
        if (!leftVendorId.equals(rightVendorId)) {
            return false;
        }
        if (!StringUtils.hasText(leftUserId) || !StringUtils.hasText(rightUserId)) {
            return false;
        }
        return leftUserId.equalsIgnoreCase(rightUserId);
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

    private boolean equalsIgnoreCase(Object value, String expected) {
        return expected.equalsIgnoreCase(stringValue(value).trim());
    }

    private boolean isVendorSecurityOperational(Map<String, Object> vendor) {
        if (vendor == null) {
            return false;
        }
        return isTruthy(vendor.get(ACTIVE_FIELD))
                && !isTruthy(vendor.get("deleted"))
                && equalsIgnoreCase(vendor.get("status"), "ACTIVE");
    }

    private void revokeSessionsForVendorPrincipals(UUID vendorId, String internalAuth, String userSub, String userRoles) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> row : vendorClient.listVendorUsers(vendorId, internalAuth, userSub, userRoles)) {
            String keycloakUserId = stringOrNull(row.get(KEYCLOAK_USER_ID_FIELD));
            if (StringUtils.hasText(keycloakUserId)) {
                ids.add(keycloakUserId);
            }
        }
        for (Map<String, Object> row : accessClient.listVendorStaff(vendorId, internalAuth, userRoles, vendorId)) {
            String keycloakUserId = stringOrNull(row.get(KEYCLOAK_USER_ID_FIELD));
            if (StringUtils.hasText(keycloakUserId)) {
                ids.add(keycloakUserId);
            }
        }
        keycloakVendorAdminManagementService.logoutUserSessionsBulk(ids);
    }

    private String normalizeVendorUserRole(String requestedRole) {
        if (!StringUtils.hasText(requestedRole)) {
            return MANAGER_ROLE;
        }
        String normalized = requestedRole.trim().toUpperCase();
        return switch (normalized) {
            case OWNER_ROLE, MANAGER_ROLE -> normalized;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vendorUserRole must be OWNER or MANAGER");
        };
    }

    private Set<UUID> resolveLinkedVendorIds(String normalizedUserSub, String internalAuth) {
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (Map<String, Object> membership : vendorClient.listAccessibleVendorMembershipsByKeycloakUser(normalizedUserSub, internalAuth)) {
            if (membership == null) {
                continue;
            }
            UUID vendorId = tryParseUuid(membership.get(VENDOR_ID_FIELD));
            if (vendorId != null) {
                vendorIds.add(vendorId);
            }
        }
        return Set.copyOf(vendorIds);
    }

    private Set<String> collectActiveVendorUserIds(UUID vendorId, String internalAuth) {
        Set<String> linkedIds = new LinkedHashSet<>();
        for (Map<String, Object> vendorUser : vendorClient.listVendorUsersInternal(vendorId, internalAuth)) {
            addNormalizedUserId(linkedIds, vendorUser, ACTIVE_FIELD, false);
        }
        return linkedIds;
    }

    private Set<String> collectActiveVendorStaffIds(UUID vendorId, String internalAuth) {
        Set<String> linkedIds = new LinkedHashSet<>();
        for (Map<String, Object> vendorStaff : accessClient.listVendorStaff(vendorId, internalAuth, VENDOR_ADMIN_ROLE, vendorId)) {
            addNormalizedUserId(linkedIds, vendorStaff, ACTIVE_FIELD, true);
        }
        return linkedIds;
    }

    private void addNormalizedUserId(
            Set<String> linkedIds,
            Map<String, Object> row,
            String activeField,
            boolean skipDeleted
    ) {
        if (row == null || !isTruthy(row.get(activeField)) || (skipDeleted && isTruthy(row.get("deleted")))) {
            return;
        }
        String linkedUserId = stringOrNull(row.get(KEYCLOAK_USER_ID_FIELD));
        if (StringUtils.hasText(linkedUserId)) {
            linkedIds.add(linkedUserId.trim().toLowerCase(Locale.ROOT));
        }
    }

    private Map<String, Object> ensureVendorStaffMembership(
            Map<String, Object> existing,
            VendorStaffMembershipSyncContext syncContext
    ) {
        if (!existing.isEmpty()) {
            return existing;
        }
        if (!syncContext.desiredActive()) {
            return Map.of();
        }
        if (!StringUtils.hasText(syncContext.incomingEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Access service response is missing vendor staff email");
        }
        Map<String, Object> payload = buildVendorManagerMembershipPayload(
                syncContext.keycloakUserId(),
                syncContext.incomingEmail(),
                syncContext.incomingDisplayName(),
                true
        );
        try {
            vendorClient.addVendorUser(
                    syncContext.vendorId(),
                    payload,
                    syncContext.internalAuth(),
                    syncContext.userSub(),
                    syncContext.userRoles()
            );
            return Map.of();
        } catch (DownstreamHttpException ex) {
            if (ex.getStatusCode().value() != 409) {
                throw ex;
            }
            Map<String, Object> recovered = findMembershipByKeycloakUserId(
                    vendorClient.listVendorUsers(
                            syncContext.vendorId(),
                            syncContext.internalAuth(),
                            syncContext.userSub(),
                            syncContext.userRoles()
                    ),
                    syncContext.keycloakUserId()
            );
            if (recovered.isEmpty()) {
                throw ex;
            }
            return recovered;
        }
    }

    private boolean isOwnerMembership(Map<String, Object> membership) {
        return OWNER_ROLE.equalsIgnoreCase(stringValue(membership.get("role")).trim());
    }

    private String resolveDisplayName(String requestedDisplayName, String firstName, String lastName, String fallbackEmail) {
        if (StringUtils.hasText(requestedDisplayName)) {
            return requestedDisplayName.trim();
        }
        String fullName = ((StringUtils.hasText(firstName) ? firstName.trim() : "") + " "
                + (StringUtils.hasText(lastName) ? lastName.trim() : "")).trim();
        if (StringUtils.hasText(fullName)) {
            return fullName;
        }
        return fallbackEmail;
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " exceeds max length " + maxLength);
        }
        return normalized;
    }

    private record VendorStaffMembershipSyncContext(
            UUID vendorId,
            String keycloakUserId,
            boolean desiredActive,
            String incomingEmail,
            String incomingDisplayName,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
    }
}
