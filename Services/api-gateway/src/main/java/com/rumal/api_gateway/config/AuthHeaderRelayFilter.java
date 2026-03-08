package com.rumal.api_gateway.config;

import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@NullMarked
@Component
public class AuthHeaderRelayFilter implements GlobalFilter, Ordered {

    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final String GUEST_CART_ID_COOKIE = "rs_guest_cart_id";
    private static final String GUEST_CART_SIGNATURE_COOKIE = "rs_guest_cart_sig";

    private final String internalSharedSecret;
    private final String guestCartSigningSecret;
    private final String claimsNamespace;
    private final KeycloakRoleClaims keycloakRoleClaims;
    private final TrustedProxyResolver trustedProxyResolver;

    public AuthHeaderRelayFilter(
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret,
            @Value("${guest.cart.signing-secret:dev-guest-cart-signing-secret-change-me}") String guestCartSigningSecret,
            @Value("${keycloak.claims-namespace:}") String claimsNamespace,
            KeycloakRoleClaims keycloakRoleClaims,
            TrustedProxyResolver trustedProxyResolver
    ) {
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
        this.guestCartSigningSecret = guestCartSigningSecret == null ? "" : guestCartSigningSecret.trim();
        if (claimsNamespace == null || claimsNamespace.isBlank()) {
            this.claimsNamespace = "";
        } else {
            this.claimsNamespace = claimsNamespace.endsWith("/") ? claimsNamespace : claimsNamespace + "/";
        }
        this.keycloakRoleClaims = keycloakRoleClaims;
        this.trustedProxyResolver = trustedProxyResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (isLocalGatewayEndpoint(exchange)) {
            return chain.filter(exchange);
        }

        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Sub");
                    headers.remove("X-User-Email");
                    headers.remove("X-User-Email-Verified");
                    headers.remove("X-User-Roles");
                    headers.remove("X-User-Vendor-Id");
                    headers.remove("X-Caller-Vendor-Id");
                    headers.remove("X-Actor-Tenant-Id");
                    headers.remove("X-Audit-Client-Ip");
                    headers.remove("X-Audit-User-Agent");
                    headers.remove("X-Guest-Cart-Id");
                    headers.remove("X-Internal-Auth");
                    headers.remove("X-Internal-Signature");
                    headers.remove("X-Internal-Timestamp");
                    headers.remove("X-Internal-Path");
                    headers.remove("X-Internal-Body-Hash");
                    if (!internalSharedSecret.isBlank()) {
                        headers.set("X-Internal-Auth", internalSharedSecret);
                    }
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
                    String tenantId = keycloakRoleClaims.extractTenantId(auth.getToken());
                    if (StringUtils.hasText(tenantId)) {
                        requestBuilder.headers(headers -> headers.set("X-Actor-Tenant-Id", tenantId));
                    }
                    String clientIp = trustedProxyResolver.resolveClientIp(sanitizedExchange);
                    if (StringUtils.hasText(clientIp)) {
                        requestBuilder.headers(headers -> headers.set("X-Audit-Client-Ip", clientIp));
                    }
                    String userAgent = sanitizeUserAgent(sanitizedExchange.getRequest().getHeaders().getFirst("User-Agent"));
                    if (StringUtils.hasText(userAgent)) {
                        requestBuilder.headers(headers -> headers.set("X-Audit-User-Agent", userAgent));
                    }
                    if (!internalSharedSecret.isBlank()) {
                        requestBuilder.headers(headers -> headers.set("X-Internal-Auth", internalSharedSecret));
                    }

                    return sanitizedExchange.mutate().request(requestBuilder.build()).build();
                })
                .defaultIfEmpty(sanitizedExchange)
                .map(this::attachGuestCartHeader)
                .flatMap(ex -> attachHmacSignature(ex, chain));
    }

    private boolean isLocalGatewayEndpoint(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.startsWith("/auth/")
                || path.startsWith("/internal/")
                || path.startsWith("/fallback/");
    }

    private ServerWebExchange attachGuestCartHeader(ServerWebExchange exchange) {
        String guestCartId = resolveSignedGuestCartId(exchange.getRequest());
        if (!StringUtils.hasText(guestCartId)) {
            return exchange;
        }
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set("X-Guest-Cart-Id", guestCartId))
                .build();
        return exchange.mutate().request(request).build();
    }

    private String resolveSignedGuestCartId(ServerHttpRequest request) {
        if (!StringUtils.hasText(guestCartSigningSecret)) {
            return null;
        }
        HttpCookie cartIdCookie = request.getCookies().getFirst(GUEST_CART_ID_COOKIE);
        HttpCookie signatureCookie = request.getCookies().getFirst(GUEST_CART_SIGNATURE_COOKIE);
        String guestCartId = cartIdCookie == null ? null : cartIdCookie.getValue();
        String signature = signatureCookie == null ? null : signatureCookie.getValue();
        if (!StringUtils.hasText(guestCartId) || !StringUtils.hasText(signature)) {
            return null;
        }
        String expectedSignature = computeHmac(guestCartSigningSecret, guestCartId.trim());
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return guestCartId.trim();
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

    private String sanitizeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return null;
        }
        String normalized = userAgent.trim().replaceAll("[\\r\\n]+", " ");
        if (normalized.length() > MAX_USER_AGENT_LENGTH) {
            return normalized.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return normalized;
    }

    private String serializeRoles(JwtAuthenticationToken auth) {
        return String.join(",", keycloakRoleClaims.extractForwardedRoles(auth.getToken()));
    }
}
