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

@NullMarked
@Component
public class AuthHeaderRelayFilter implements GlobalFilter, Ordered {

    private final String internalSharedSecret;
    private final String claimsNamespace;

    public AuthHeaderRelayFilter(
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret,
            @Value("${auth0.claims-namespace:https://auth0.rumalg.me/claims/}") String claimsNamespace
    ) {
        this.internalSharedSecret = internalSharedSecret;
        this.claimsNamespace = claimsNamespace.endsWith("/") ? claimsNamespace : claimsNamespace + "/";
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-Auth0-Sub");
                    headers.remove("X-Auth0-Email");
                    headers.remove("X-Auth0-Email-Verified");
                    headers.remove("X-Internal-Auth");
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        return sanitizedExchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    String subject = auth.getToken().getSubject();
                    String namespacedEmail = auth.getToken().getClaimAsString(claimsNamespace + "email");
                    String fallbackEmail = auth.getToken().getClaimAsString("email");
                    Boolean emailVerified = auth.getToken().getClaimAsBoolean("email_verified");
                    if (emailVerified == null) {
                        emailVerified = auth.getToken().getClaimAsBoolean(claimsNamespace + "email_verified");
                    }
                    final String resolvedEmail = (namespacedEmail != null && !namespacedEmail.isBlank())
                            ? namespacedEmail
                            : fallbackEmail;

                    ServerHttpRequest.Builder requestBuilder = sanitizedExchange.getRequest().mutate();
                    if (subject != null && !subject.isBlank()) {
                        requestBuilder.headers(headers -> headers.set("X-Auth0-Sub", subject));
                    }
                    if (resolvedEmail != null && !resolvedEmail.isBlank()) {
                        requestBuilder.headers(headers -> headers.set("X-Auth0-Email", resolvedEmail));
                    }
                    if (emailVerified != null) {
                        Boolean finalEmailVerified = emailVerified;
                        requestBuilder.headers(headers ->
                                headers.set("X-Auth0-Email-Verified", String.valueOf(finalEmailVerified)));
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
}
