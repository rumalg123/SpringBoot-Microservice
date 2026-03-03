package com.rumal.analytics_service.client;

import com.rumal.analytics_service.client.dto.VendorAccessMembershipLookup;
import com.rumal.analytics_service.client.dto.VendorStaffAccessLookup;
import com.rumal.analytics_service.exception.DownstreamHttpException;
import com.rumal.analytics_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class AccessScopeClient {

    private static final String VENDOR_MEMBERSHIP_URL = "http://vendor-service/internal/vendors/access/by-keycloak/";
    private static final String VENDOR_STAFF_ACCESS_URL = "http://access-service/internal/access/vendors/by-keycloak/";
    private static final String VENDOR_ANALYTICS_READ = "vendor.analytics.read";
    private static final String VENDOR_REPORTS_READ = "vendor.reports.read";

    private final RestClient restClient;
    private final String internalAuth;

    public AccessScopeClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalAuth
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalAuth = internalAuth;
    }

    public Set<UUID> listVendorMembershipVendorIds(String keycloakUserId) {
        String encodedUserId = UriUtils.encodePathSegment(keycloakUserId, StandardCharsets.UTF_8);
        List<VendorAccessMembershipLookup> memberships = getList(
                VENDOR_MEMBERSHIP_URL + encodedUserId,
                new ParameterizedTypeReference<>() {
                }
        );
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (VendorAccessMembershipLookup membership : memberships) {
            if (membership != null && membership.vendorId() != null) {
                vendorIds.add(membership.vendorId());
            }
        }
        return Set.copyOf(vendorIds);
    }

    public Set<UUID> listVendorStaffAnalyticsVendorIds(String keycloakUserId) {
        String encodedUserId = UriUtils.encodePathSegment(keycloakUserId, StandardCharsets.UTF_8);
        List<VendorStaffAccessLookup> rows = getList(
                VENDOR_STAFF_ACCESS_URL + encodedUserId,
                new ParameterizedTypeReference<>() {
                }
        );
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (VendorStaffAccessLookup row : rows) {
            if (row == null || row.vendorId() == null || !row.active()) {
                continue;
            }
            if (hasAnalyticsAccess(row.permissions())) {
                vendorIds.add(row.vendorId());
            }
        }
        return Set.copyOf(vendorIds);
    }

    private boolean hasAnalyticsAccess(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        for (String permission : permissions) {
            String normalized = normalizePermissionCode(permission);
            if (VENDOR_ANALYTICS_READ.equals(normalized) || VENDOR_REPORTS_READ.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePermissionCode(String permission) {
        if (permission == null) {
            return "";
        }
        String normalized = permission.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.contains(".")) {
            return normalized;
        }
        return switch (normalized) {
            case "analytics_read" -> VENDOR_ANALYTICS_READ;
            case "reports_read" -> VENDOR_REPORTS_READ;
            default -> normalized;
        };
    }

    private <T> List<T> getList(String url, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> result = restClient.get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
            return result == null ? List.of() : result;
        } catch (RestClientResponseException ex) {
            throw new DownstreamHttpException(ex.getStatusCode(), "Access scope HTTP error: " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Access scope service unavailable", ex);
        }
    }
}
