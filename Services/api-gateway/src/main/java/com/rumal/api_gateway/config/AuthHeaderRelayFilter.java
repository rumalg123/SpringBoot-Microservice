package com.rumal.api_gateway.config;

import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NullMarked
@Component
public class AuthHeaderRelayFilter implements GlobalFilter, Ordered {

    private final String internalSharedSecret;
    private final String claimsNamespace;

    public AuthHeaderRelayFilter(
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret,
            @Value("${keycloak.claims-namespace:}") String claimsNamespace
    ) {
        this.internalSharedSecret = internalSharedSecret;
        if (claimsNamespace.isBlank()) {
            this.claimsNamespace = "";
        } else {
            this.claimsNamespace = claimsNamespace.endsWith("/") ? claimsNamespace : claimsNamespace + "/";
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Sub");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Email-Verified");
                    headers.remove("X-User-Roles");
                    headers.remove("X-Internal-Auth");
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        return sanitizedExchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    String subject = auth.getToken().getSubject();
                    String namespacedEmail = claimsNamespace.isBlank()
                            ? null
                            : auth.getToken().getClaimAsString(claimsNamespace + "email");
                    String fallbackEmail = auth.getToken().getClaimAsString("email");
                    Boolean emailVerified = auth.getToken().getClaimAsBoolean("email_verified");
                    if (emailVerified == null && !claimsNamespace.isBlank()) {
                        emailVerified = auth.getToken().getClaimAsBoolean(claimsNamespace + "email_verified");
                    }
                    final String resolvedEmail = (namespacedEmail != null && !namespacedEmail.isBlank())
                            ? namespacedEmail
                            : fallbackEmail;

                    ServerHttpRequest.Builder requestBuilder = sanitizedExchange.getRequest().mutate();
                    if (subject != null && !subject.isBlank()) {
                        requestBuilder.headers(headers -> headers.set("X-User-Sub", subject));
                    }
                    if (resolvedEmail != null && !resolvedEmail.isBlank()) {
                        requestBuilder.headers(headers -> headers.set("X-User-Email", resolvedEmail));
                    }
                    if (emailVerified != null) {
                        Boolean finalEmailVerified = emailVerified;
                        requestBuilder.headers(headers ->
                                headers.set("X-User-Email-Verified", String.valueOf(finalEmailVerified)));
                    }
                    String serializedRoles = serializeRoles(auth);
                    if (!serializedRoles.isBlank()) {
                        requestBuilder.headers(headers -> headers.set("X-User-Roles", serializedRoles));
                    }
                    if (!internalSharedSecret.isBlank()) {
                        requestBuilder.headers(headers -> headers.set("X-Internal-Auth", internalSharedSecret));
                    }

                    return sanitizedExchange.mutate().request(requestBuilder.build()).build();
                })
                .defaultIfEmpty(sanitizedExchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private String serializeRoles(JwtAuthenticationToken auth) {
        Set<String> roles = new LinkedHashSet<>();

        List<String> directRoles = auth.getToken().getClaimAsStringList("roles");
        if (directRoles != null) {
            directRoles.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(roles::add);
        }

        if (!claimsNamespace.isBlank()) {
            List<String> namespacedRoles = auth.getToken().getClaimAsStringList(claimsNamespace + "roles");
            if (namespacedRoles != null) {
                namespacedRoles.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .forEach(roles::add);
            }
        }

        roles.addAll(extractRoles(auth.getToken().getClaim("realm_access")));

        Map<String, Object> resourceAccess = auth.getToken().getClaim("resource_access");
        if (resourceAccess != null && !resourceAccess.isEmpty()) {
            for (Object clientAccess : resourceAccess.values()) {
                roles.addAll(extractRoles(clientAccess));
            }
        }

        return String.join(",", roles);
    }

    private Set<String> extractRoles(Object claimValue) {
        if (!(claimValue instanceof Map<?, ?> map)) {
            return Set.of();
        }
        Object rolesValue = map.get("roles");
        if (!(rolesValue instanceof List<?> rawRoles)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (Object rawRole : rawRoles) {
            if (rawRole instanceof String role && !role.isBlank()) {
                roles.add(role.trim());
            }
        }
        return roles;
    }
}
