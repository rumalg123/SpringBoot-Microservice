package com.rumal.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final String jwkSetUri;
    private final String audienceConfig;
    private final boolean acceptAzpAsAudience;
    private final KeycloakRoleClaims keycloakRoleClaims;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${keycloak.jwk-set-uri:}") String jwkSetUri,
            @Value("${keycloak.audience}") String audience,
            @Value("${keycloak.accept-azp-as-audience:false}") boolean acceptAzpAsAudience,
            KeycloakRoleClaims keycloakRoleClaims
    ) {
        this.issuerUri = issuerUri;
        this.jwkSetUri = jwkSetUri;
        this.audienceConfig = audience;
        this.acceptAzpAsAudience = acceptAzpAsAudience;
        this.keycloakRoleClaims = keycloakRoleClaims;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/products/*/view").permitAll()
                        .pathMatchers(HttpMethod.GET, "/categories", "/categories/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/posters", "/posters/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/posters/*/click", "/posters/*/impression").permitAll()
                        .pathMatchers("/reviews/vendor/**").access(this::hasVendorAccess)
                        .pathMatchers("/reviews/me", "/reviews/me/**").access(this::hasCustomerAccess)
                        .pathMatchers(HttpMethod.GET, "/reviews", "/reviews/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/search", "/search/**").permitAll()
                        .pathMatchers("/webhooks/**").permitAll()
                        .pathMatchers("/analytics/admin/**").access(this::hasSuperAdminOrPlatformStaffAccess)
                        .pathMatchers("/admin/dashboard/**").access(this::hasSuperAdminOrPlatformStaffAccess)
                        .pathMatchers("/analytics/vendor/**").access(this::hasAnyScopedAdminAccess)
                        .pathMatchers("/analytics/customer/**").access(this::hasCustomerAccess)
                        .pathMatchers("/orders/vendor/me", "/orders/vendor/me/**").access(this::hasVendorAccess)
                        .pathMatchers("/payments/vendor/me", "/payments/vendor/me/**").access(this::hasVendorAccess)
                        .pathMatchers("/inventory/vendor/me", "/inventory/vendor/me/**").access(this::hasVendorAccess)
                        .pathMatchers("/vendors/me", "/vendors/me/**").access(this::hasVendorAccess)
                        .pathMatchers(HttpMethod.GET, "/vendors", "/vendors/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info", "/customers/register", "/fallback/**").permitAll()
                        .pathMatchers("/auth/logout", "/auth/session", "/auth/resend-verification").authenticated()
                        .pathMatchers(HttpMethod.GET, "/wishlist/shared", "/wishlist/shared/**").permitAll()
                        .pathMatchers("/customers/me", "/customers/me/**", "/customers/register-identity", "/orders/me", "/orders/me/**", "/cart/me", "/cart/me/**", "/wishlist/me", "/wishlist/me/**", "/promotions/me", "/promotions/me/**", "/payments/me", "/payments/me/**")
                        .access(this::hasCustomerAccess)
                        .pathMatchers(HttpMethod.GET, "/promotions", "/promotions/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/personalization/sessions/merge").authenticated()
                        .pathMatchers("/personalization/me", "/personalization/me/**").authenticated()
                        .pathMatchers("/personalization/**").permitAll()
                        .pathMatchers("/admin/vendors/**").access(this::hasSuperAdminOrPlatformStaffAccess)
                        .pathMatchers("/admin/platform-staff/**").access(this::hasSuperAdminAccess)
                        .pathMatchers("/admin/vendor-staff/**", "/admin/keycloak/users/**", "/admin/access-audit/**").access(this::hasSuperAdminOrVendorAdminAccess)
                        .pathMatchers("/admin/posters/**", "/admin/categories/**").access(this::hasSuperAdminOrPlatformStaffAccess)
                        .pathMatchers("/admin/products/*/approve", "/admin/products/*/reject", "/admin/products/export", "/admin/products/import")
                        .access(this::hasSuperAdminOrPlatformStaffAccess)
                        .pathMatchers("/admin/api-keys/**", "/admin/sessions/**").access(this::hasSuperAdminAccess)
                        .pathMatchers("/admin/orders/**", "/admin/vendor-orders/**", "/admin/products/**", "/admin/promotions/**", "/admin/inventory/**").access(this::hasAnyScopedAdminAccess)
                        .pathMatchers("/admin/reviews/**").access(this::hasSuperAdminOrPlatformStaffAccess)
                        .pathMatchers("/admin/payments/**").access(this::hasSuperAdminOrPlatformStaffAccess)
                        .pathMatchers("/admin/me/**").access(this::hasAnyScopedAdminAccess)
                        .pathMatchers("/admin/**").access(this::hasSuperAdminAccess)
                        .pathMatchers("/customer-service/**", "/order-service/**", "/cart-service/**", "/wishlist-service/**", "/admin-service/**", "/product-service/**", "/poster-service/**", "/vendor-service/**", "/promotion-service/**", "/access-service/**", "/payment-service/**", "/inventory-service/**", "/review-service/**", "/analytics-service/**", "/personalization-service/**", "/search-service/**", "/discovery-server/**").denyAll()
                        .pathMatchers("/customers/**", "/orders/**", "/cart/**", "/wishlist/**", "/promotions/**").denyAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(resolveJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audienceConfig, acceptAzpAsAudience);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return jwtDecoder;
    }

    private String resolveJwkSetUri() {
        if (StringUtils.hasText(jwkSetUri)) {
            return jwkSetUri.trim();
        }

        String normalizedIssuer = issuerUri == null ? "" : issuerUri.trim();
        if (!StringUtils.hasText(normalizedIssuer)) {
            throw new IllegalArgumentException("spring.security.oauth2.resourceserver.jwt.issuer-uri is required");
        }
        if (normalizedIssuer.endsWith("/")) {
            normalizedIssuer = normalizedIssuer.substring(0, normalizedIssuer.length() - 1);
        }
        return normalizedIssuer + "/protocol/openid-connect/certs";
    }

    private Mono<AuthorizationResult> hasSuperAdminAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(
                        keycloakRoleClaims.isEmailVerified(jwt) && keycloakRoleClaims.hasRole(jwt, "super_admin")
                ))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<AuthorizationResult> hasSuperAdminOrVendorAdminAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(
                        keycloakRoleClaims.isEmailVerified(jwt)
                                && (keycloakRoleClaims.hasRole(jwt, "super_admin")
                                || keycloakRoleClaims.hasRole(jwt, "vendor_admin"))
                ))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<AuthorizationResult> hasSuperAdminOrPlatformStaffAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(
                        keycloakRoleClaims.isEmailVerified(jwt)
                                && (keycloakRoleClaims.hasRole(jwt, "super_admin")
                                || keycloakRoleClaims.hasRole(jwt, "platform_staff"))
                ))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<AuthorizationResult> hasAnyScopedAdminAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(
                        keycloakRoleClaims.isEmailVerified(jwt)
                                && (keycloakRoleClaims.hasRole(jwt, "super_admin")
                                || keycloakRoleClaims.hasRole(jwt, "platform_staff")
                                || keycloakRoleClaims.hasRole(jwt, "vendor_admin")
                                || keycloakRoleClaims.hasRole(jwt, "vendor_staff"))
                ))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<AuthorizationResult> hasVendorAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(
                        keycloakRoleClaims.isEmailVerified(jwt)
                                && (keycloakRoleClaims.hasRole(jwt, "vendor_admin")
                                || keycloakRoleClaims.hasRole(jwt, "vendor_staff"))
                ))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<AuthorizationResult> hasCustomerAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(
                        keycloakRoleClaims.isEmailVerified(jwt) && keycloakRoleClaims.hasRole(jwt, "customer")
                ))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
