package com.rumal.admin_service.service;

import com.rumal.admin_service.auth.KeycloakVendorAdminManagementService;
import com.rumal.admin_service.client.VendorClient;
import com.rumal.admin_service.dto.VendorAdminOnboardRequest;
import com.rumal.admin_service.dto.VendorAdminOnboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminVendorService {

    private final VendorClient vendorClient;
    private final KeycloakVendorAdminManagementService keycloakVendorAdminManagementService;

    public List<Map<String, Object>> listAll(String internalAuth) {
        return vendorClient.listAll(internalAuth);
    }

    public List<Map<String, Object>> listDeleted(String internalAuth) {
        return vendorClient.listDeleted(internalAuth);
    }

    public Map<String, Object> getById(UUID id, String internalAuth) {
        return vendorClient.getById(id, internalAuth);
    }

    public Map<String, Object> create(Map<String, Object> request, String internalAuth) {
        return vendorClient.create(request, internalAuth);
    }

    public Map<String, Object> update(UUID id, Map<String, Object> request, String internalAuth) {
        return vendorClient.update(id, request, internalAuth);
    }

    public void delete(UUID id, String internalAuth) {
        vendorClient.delete(id, internalAuth);
    }

    public Map<String, Object> restore(UUID id, String internalAuth) {
        return vendorClient.restore(id, internalAuth);
    }

    public List<Map<String, Object>> listVendorUsers(UUID vendorId, String internalAuth) {
        return vendorClient.listVendorUsers(vendorId, internalAuth);
    }

    public List<Map<String, Object>> listAccessibleVendorMembershipsByKeycloakUser(String keycloakUserId, String internalAuth) {
        return vendorClient.listAccessibleVendorMembershipsByKeycloakUser(keycloakUserId, internalAuth);
    }

    public Map<String, Object> addVendorUser(UUID vendorId, Map<String, Object> request, String internalAuth) {
        return vendorClient.addVendorUser(vendorId, request, internalAuth);
    }

    public Map<String, Object> updateVendorUser(UUID vendorId, UUID membershipId, Map<String, Object> request, String internalAuth) {
        return vendorClient.updateVendorUser(vendorId, membershipId, request, internalAuth);
    }

    public void removeVendorUser(UUID vendorId, UUID membershipId, String internalAuth) {
        vendorClient.removeVendorUser(vendorId, membershipId, internalAuth);
    }

    public VendorAdminOnboardResponse onboardVendorAdmin(UUID vendorId, VendorAdminOnboardRequest request, String internalAuth) {
        // Fail fast before touching Keycloak if vendor does not exist.
        vendorClient.getById(vendorId, internalAuth);

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
                "keycloakUserId", managedUser.id(),
                "email", managedUser.email(),
                "displayName", displayName,
                "role", role,
                "active", true
        );

        Map<String, Object> membership = upsertVendorMembership(vendorId, managedUser.id(), membershipRequest, internalAuth);

        return new VendorAdminOnboardResponse(
                vendorId,
                managedUser.created(),
                managedUser.id(),
                managedUser.email(),
                managedUser.firstName(),
                managedUser.lastName(),
                membership
        );
    }

    private Map<String, Object> upsertVendorMembership(UUID vendorId, String keycloakUserId, Map<String, Object> membershipRequest, String internalAuth) {
        List<Map<String, Object>> memberships = vendorClient.listVendorUsers(vendorId, internalAuth);
        Map<String, Object> existing = memberships.stream()
                .filter(m -> keycloakUserId.equalsIgnoreCase(stringValue(m.get("keycloakUserId"))))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            return vendorClient.addVendorUser(vendorId, membershipRequest, internalAuth);
        }

        UUID membershipId = parseUuid(existing.get("id"), "vendor membership id");
        return vendorClient.updateVendorUser(vendorId, membershipId, membershipRequest, internalAuth);
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

    private String normalizeVendorUserRole(String requestedRole) {
        if (!StringUtils.hasText(requestedRole)) {
            return "MANAGER";
        }
        String normalized = requestedRole.trim().toUpperCase();
        return switch (normalized) {
            case "OWNER", "MANAGER" -> normalized;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vendorUserRole must be OWNER or MANAGER");
        };
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
}
