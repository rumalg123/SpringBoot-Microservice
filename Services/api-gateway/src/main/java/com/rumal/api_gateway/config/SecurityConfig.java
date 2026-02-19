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
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final String audience;
    private final String claimsNamespace;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${auth0.audience}") String audience,
            @Value("${auth0.claims-namespace:https://auth0.rumalg.me/claims/}") String claimsNamespace
    ) {
        this.issuerUri = issuerUri;
        this.audience = audience;
        this.claimsNamespace = claimsNamespace.endsWith("/") ? claimsNamespace : claimsNamespace + "/";
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
                        .pathMatchers("/customers/me", "/customers/register-auth0", "/orders/me", "/orders/me/**")
                        .access(this::hasVerifiedEmailAccess)
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
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audience);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return jwtDecoder;
    }

    private Mono<AuthorizationResult> hasAdminAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(hasAdminPermission(jwt) || hasAdminRole(jwt)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private Mono<AuthorizationResult> hasVerifiedEmailAccess(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(JwtAuthenticationToken::getToken)
                .map(jwt -> (AuthorizationResult) new AuthorizationDecision(isEmailVerifiedOrUnknown(jwt)))
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    private boolean isEmailVerifiedOrUnknown(Jwt jwt) {
        Boolean standard = jwt.getClaimAsBoolean("email_verified");
        if (standard != null) {
            return standard;
        }

        Boolean namespaced = jwt.getClaimAsBoolean(claimsNamespace + "email_verified");
        if (namespaced != null) {
            return namespaced;
        }

        return true;
    }

    private boolean hasAdminPermission(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList("permissions");
        return permissions != null && permissions.contains("read:admin-orders");
    }

    private boolean hasAdminRole(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(claimsNamespace + "roles");
        if (roles == null || roles.isEmpty()) {
            roles = jwt.getClaimAsStringList("roles");
        }
        return roles != null && roles.stream().anyMatch("admin"::equalsIgnoreCase);
    }
}
