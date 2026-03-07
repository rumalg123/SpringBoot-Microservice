package com.rumal.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRoleClaimsTests {

    @Test
    void extractForwardedRolesNormalizesAndFiltersToTargetRoles() {
        KeycloakRoleClaims claims = new KeycloakRoleClaims("");

        Set<String> roles = claims.extractForwardedRoles(jwt(builder -> {
            builder.claim("roles", List.of("ROLE_SUPER_ADMIN", "platform_admin", "manage-account"));
            builder.claim("realm_access", Map.of("roles", List.of("vendor_staff", "offline_access")));
            builder.claim("resource_access", Map.of("catalog", Map.of("roles", List.of("customer", "uma_authorization"))));
        }));

        assertEquals(Set.of("super_admin", "vendor_staff", "customer"), roles);
        assertFalse(roles.contains("platform_admin"));
        assertFalse(roles.contains("manage_account"));
    }

    @Test
    void emailVerifiedFallsBackToNamespacedClaim() {
        KeycloakRoleClaims claims = new KeycloakRoleClaims("https://claims.example.test");

        boolean verified = claims.isEmailVerified(jwt(builder ->
                builder.claim("https://claims.example.test/email_verified", true)
        ));

        assertTrue(verified);
    }

    @Test
    void hasRoleMatchesNormalizedRealmRoleValues() {
        KeycloakRoleClaims claims = new KeycloakRoleClaims("");

        boolean hasRole = claims.hasRole(jwt(builder ->
                builder.claim("realm_access", Map.of("roles", List.of("ROLE-VENDOR-ADMIN")))
        ), "vendor_admin");

        assertTrue(hasRole);
    }

    private Jwt jwt(java.util.function.Consumer<Jwt.Builder> customizer) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", "https://keycloak.example.test/realms/rumal")
                .issuedAt(Instant.parse("2026-03-07T00:00:00Z"))
                .expiresAt(Instant.parse("2026-03-07T01:00:00Z"));
        customizer.accept(builder);
        return builder.build();
    }
}
