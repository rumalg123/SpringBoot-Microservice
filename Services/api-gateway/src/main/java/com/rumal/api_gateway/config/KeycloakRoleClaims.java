package com.rumal.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class KeycloakRoleClaims {

    private static final Set<String> FORWARDED_ROLES = Set.of(
            "super_admin",
            "platform_staff",
            "customer",
            "vendor_admin",
            "vendor_staff"
    );

    private final String claimsNamespace;

    public KeycloakRoleClaims(@Value("${keycloak.claims-namespace:}") String claimsNamespace) {
        if (!StringUtils.hasText(claimsNamespace)) {
            this.claimsNamespace = "";
        } else {
            this.claimsNamespace = claimsNamespace.endsWith("/") ? claimsNamespace : claimsNamespace + "/";
        }
    }

    public boolean isEmailVerified(Jwt jwt) {
        Boolean standard = jwt.getClaimAsBoolean("email_verified");
        if (standard != null) {
            return standard;
        }

        if (!claimsNamespace.isBlank()) {
            Boolean namespaced = jwt.getClaimAsBoolean(claimsNamespace + "email_verified");
            if (namespaced != null) {
                return namespaced;
            }
        }

        return false;
    }

    public boolean hasRole(Jwt jwt, String requiredRole) {
        if (!StringUtils.hasText(requiredRole)) {
            return false;
        }
        String normalizedRole = normalizeRole(requiredRole);
        return extractNormalizedRoles(jwt).contains(normalizedRole);
    }

    public Set<String> extractForwardedRoles(Jwt jwt) {
        Set<String> allowedRoles = new LinkedHashSet<>();
        for (String role : extractNormalizedRoles(jwt)) {
            if (FORWARDED_ROLES.contains(role)) {
                allowedRoles.add(role);
            }
        }
        return Collections.unmodifiableSet(allowedRoles);
    }

    private Set<String> extractNormalizedRoles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        addRoles(roles, jwt.getClaimAsStringList("roles"));

        if (!claimsNamespace.isBlank()) {
            addRoles(roles, jwt.getClaimAsStringList(claimsNamespace + "roles"));
        }

        addRoles(roles, extractRoles(jwt.getClaim("realm_access")));

        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null && !resourceAccess.isEmpty()) {
            for (Object clientAccess : resourceAccess.values()) {
                addRoles(roles, extractRoles(clientAccess));
            }
        }

        return roles;
    }

    private void addRoles(Set<String> sink, List<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) {
            return;
        }
        for (String rawRole : rawRoles) {
            String normalized = normalizeRole(rawRole);
            if (!normalized.isEmpty()) {
                sink.add(normalized);
            }
        }
    }

    private List<String> extractRoles(Object claimValue) {
        if (!(claimValue instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object rolesValue = map.get("roles");
        if (!(rolesValue instanceof List<?> rawRoles)) {
            return List.of();
        }
        return rawRoles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return "";
        }
        String normalized = role.trim().toLowerCase();
        if (normalized.startsWith("role_")) {
            normalized = normalized.substring("role_".length());
        } else if (normalized.startsWith("role-")) {
            normalized = normalized.substring("role-".length());
        } else if (normalized.startsWith("role:")) {
            normalized = normalized.substring("role:".length());
        }
        return normalized.replace('-', '_').replace(' ', '_');
    }
}
