package com.rumal.api_gateway.config;

import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
                    headers.remove("X-Internal-Signature");
                    headers.remove("X-Internal-Timestamp");
                    headers.remove("X-Internal-Path");
                    headers.remove("X-Internal-Body-Hash");
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
                .flatMap(ex -> attachHmacSignature(ex, chain));
    }

    private Mono<Void> attachHmacSignature(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (internalSharedSecret.isBlank()) {
            return chain.filter(exchange);
        }

        return resolveBodyBytes(exchange)
                .flatMap(bodyBytes -> {
                    String bodyHash = bodyBytes.length > 0 ? sha256Hex(bodyBytes) : "";
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String method = exchange.getRequest().getMethod().name();
                    String path = exchange.getRequest().getURI().getRawPath();

                    String payload = timestamp + ":" + method + ":" + path + ":" + bodyHash;
                    String signature = computeHmac(internalSharedSecret, payload);

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.set("X-Internal-Timestamp", timestamp);
                                headers.set("X-Internal-Signature", signature);
                                headers.set("X-Internal-Path", path);
                                headers.set("X-Internal-Body-Hash", bodyHash);
                            })
                            .build();

                    if (bodyBytes.length > 0) {
                        ServerHttpRequestDecorator decoratedRequest = new ServerHttpRequestDecorator(mutatedRequest) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(exchange.getResponse().bufferFactory().wrap(bodyBytes));
                            }
                        };
                        return chain.filter(exchange.mutate().request(decoratedRequest).build());
                    }

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    private Mono<byte[]> resolveBodyBytes(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.GET || method == HttpMethod.DELETE
                || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
            return Mono.just(new byte[0]);
        }
        return exchange.getRequest().getBody()
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .collectList()
                .map(list -> {
                    int total = list.stream().mapToInt(b -> b.length).sum();
                    byte[] combined = new byte[total];
                    int offset = 0;
                    for (byte[] chunk : list) {
                        System.arraycopy(chunk, 0, combined, offset, chunk.length);
                        offset += chunk.length;
                    }
                    return combined;
                })
                .defaultIfEmpty(new byte[0]);
    }

    private String computeHmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            return "";
        }
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
