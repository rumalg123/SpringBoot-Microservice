package com.rumal.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudienceValidatorTests {

    @Test
    void acceptsConfiguredAudienceFromAudClaim() {
        AudienceValidator validator = new AudienceValidator("rumal-api", false);

        OAuth2TokenValidatorResult result = validator.validate(jwt(builder -> builder.audience(List.of("rumal-api"))));

        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsTokenWhenAudienceOnlyAppearsInResourceAccess() {
        AudienceValidator validator = new AudienceValidator("rumal-api", false);

        OAuth2TokenValidatorResult result = validator.validate(jwt(builder -> builder.claim(
                "resource_access",
                Map.of("rumal-api", Map.of("roles", List.of("customer")))
        )));

        assertTrue(result.hasErrors());
    }

    @Test
    void rejectsAzpFallbackWhenCompatibilityFlagIsDisabled() {
        AudienceValidator validator = new AudienceValidator("rumal-api", false);

        OAuth2TokenValidatorResult result = validator.validate(jwt(builder -> builder.claim("azp", "rumal-api")));

        assertTrue(result.hasErrors());
    }

    @Test
    void acceptsAzpFallbackOnlyWhenCompatibilityFlagIsEnabled() {
        AudienceValidator validator = new AudienceValidator("rumal-api", true);

        OAuth2TokenValidatorResult result = validator.validate(jwt(builder -> builder.claim("azp", "rumal-api")));

        assertFalse(result.hasErrors());
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
