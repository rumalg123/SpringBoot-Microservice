package com.rumal.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
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

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final String audienceConfig;
    private final boolean acceptAzpAsAudience;
    private final String claimsNamespace;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${keycloak.audience}") String audience,
            @Value("${keycloak.accept-azp-as-audience:true}") boolean acceptAzpAsAudience,
            @Value("${keycloak.claims-namespace:}") String claimsNamespace
    ) {
        this.issuerUri = issuerUri;
        this.audienceConfig = audience;
        this.acceptAzpAsAudience = acceptAzpAsAudience;
        if (claimsNamespace.isBlank()) {
            this.claimsNamespace = "";
        } else {
            this.claimsNamespace = claimsNamespace.endsWith("/") ? claimsNamespace : claimsNamespace + "/";
        }
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/categories", "/categories/**").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info", "/customers/register").permitAll()
                        .pathMatchers("/auth/logout", "/auth/resend-verification").authenticated()
                        .pathMatchers("/customers/me", "/customers/register-identity", "/orders/me", "/orders/me/**")
                        .access(this::hasCustomerAccess)
                        .pathMatchers("/admin/**").access(this::hasAdminAccess)
                        .pathMatchers("/customer-service/**", "/order-service/**", "/admin-service/**", "/product-service/**", "/discovery-server/**").denyAll()
                        .pathMatchers("/customers/**", "/orders/**").denyAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withIssuerLocation(issuerUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audienceConfig, acceptAzpAsAudience);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return jwtDecoder;
    }

    private Mono<AuthorizationResult> hasAdminAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(hasRole(jwt, "super_admin")))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<AuthorizationResult> hasCustomerAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(
                        isEmailVerified(jwt) && hasRole(jwt, "customer")
                ))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean isEmailVerified(Jwt jwt) {
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

    private boolean hasRole(Jwt jwt, String requiredRole) {
        if (!StringUtils.hasText(requiredRole)) {
            return false;
        }
        String normalizedRole = requiredRole.trim();

        if (containsRole(jwt.getClaimAsStringList("roles"), normalizedRole)) {
            return true;
        }

        if (!claimsNamespace.isBlank()
                && containsRole(jwt.getClaimAsStringList(claimsNamespace + "roles"), normalizedRole)) {
            return true;
        }

        if (containsRole(extractRoles(jwt.getClaim("realm_access")), normalizedRole)) {
            return true;
        }

        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null || resourceAccess.isEmpty()) {
            return false;
        }

        for (Object clientAccess : resourceAccess.values()) {
            if (containsRole(extractRoles(clientAccess), normalizedRole)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsRole(List<String> roles, String requiredRole) {
        return roles != null && roles.stream().anyMatch(requiredRole::equalsIgnoreCase);
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
}
