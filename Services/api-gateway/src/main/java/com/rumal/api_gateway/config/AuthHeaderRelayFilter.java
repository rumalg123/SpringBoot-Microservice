package com.rumal.api_gateway.config;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
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
import java.util.List;

@NullMarked
@Component
public class AuthHeaderRelayFilter implements GlobalFilter, Ordered {

    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final String GUEST_CART_ID_COOKIE = "rs_guest_cart_id";
    private static final String GUEST_CART_SIGNATURE_COOKIE = "rs_guest_cart_sig";
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final List<String> STRIPPED_HEADERS = List.of(
            "X-User-Sub",
            "X-User-Email",
            "X-User-Email-Verified",
            "X-User-Roles",
            "X-User-Vendor-Id",
            "X-Caller-Vendor-Id",
            "X-Actor-Tenant-Id",
            "X-Audit-Client-Ip",
            "X-Audit-User-Agent",
            "X-Guest-Cart-Id",
            INTERNAL_AUTH_HEADER,
            "X-Internal-Signature",
            "X-Internal-Timestamp",
            "X-Internal-Path",
            "X-Internal-Body-Hash"
    );

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
        this.internalSharedSecret = internalSharedSecret.trim();
        this.guestCartSigningSecret = guestCartSigningSecret.trim();
        this.claimsNamespace = normalizeClaimsNamespace(claimsNamespace);
        this.keycloakRoleClaims = keycloakRoleClaims;
        this.trustedProxyResolver = trustedProxyResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (isLocalGatewayEndpoint(exchange)) {
            return chain.filter(exchange);
        }

        ServerWebExchange sanitizedExchange = sanitizeExchange(exchange);

        return sanitizedExchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> enrichAuthenticatedExchange(sanitizedExchange, auth))
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
            return "";
        }
        HttpCookie cartIdCookie = request.getCookies().getFirst(GUEST_CART_ID_COOKIE);
        HttpCookie signatureCookie = request.getCookies().getFirst(GUEST_CART_SIGNATURE_COOKIE);
        String guestCartId = cartIdCookie == null ? "" : cartIdCookie.getValue();
        String signature = signatureCookie == null ? "" : signatureCookie.getValue();
        if (!StringUtils.hasText(guestCartId) || !StringUtils.hasText(signature)) {
            return "";
        }
        String expectedSignature = computeHmac(guestCartSigningSecret, guestCartId.trim());
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
            return "";
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
            return "";
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

    private String normalizeClaimsNamespace(String value) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private ServerWebExchange sanitizeExchange(ServerWebExchange exchange) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    removeForwardedHeaders(headers);
                    applyInternalAuthHeader(headers);
                })
                .build();
        return exchange.mutate().request(sanitizedRequest).build();
    }

    private void removeForwardedHeaders(HttpHeaders headers) {
        for (String headerName : STRIPPED_HEADERS) {
            headers.remove(headerName);
        }
    }

    private void applyInternalAuthHeader(HttpHeaders headers) {
        if (!internalSharedSecret.isBlank()) {
            headers.set(INTERNAL_AUTH_HEADER, internalSharedSecret);
        }
    }

    private ServerWebExchange enrichAuthenticatedExchange(
            ServerWebExchange exchange,
            JwtAuthenticationToken authentication
    ) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    putIfHasText(headers, "X-User-Sub", authentication.getToken().getSubject());
                    putIfHasText(headers, "X-User-Email", resolveEmail(authentication));
                    putIfHasText(headers, "X-User-Email-Verified", resolveEmailVerified(authentication));
                    putIfHasText(headers, "X-User-Roles", serializeRoles(authentication));
                    putIfHasText(headers, "X-Actor-Tenant-Id", keycloakRoleClaims.extractTenantId(authentication.getToken()));
                    putIfHasText(headers, "X-Audit-Client-Ip", trustedProxyResolver.resolveClientIp(exchange));
                    putIfHasText(headers, "X-Audit-User-Agent",
                            sanitizeUserAgent(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT)));
                    applyInternalAuthHeader(headers);
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private void putIfHasText(HttpHeaders headers, String name, @Nullable String value) {
        if (StringUtils.hasText(value)) {
            headers.set(name, value.trim());
        }
    }

    private String resolveEmail(JwtAuthenticationToken authentication) {
        String namespacedEmail = claimsNamespace.isBlank()
                ? ""
                : authentication.getToken().getClaimAsString(claimsNamespace + "email");
        if (StringUtils.hasText(namespacedEmail)) {
            return namespacedEmail.trim();
        }
        String fallbackEmail = authentication.getToken().getClaimAsString("email");
        return StringUtils.hasText(fallbackEmail) ? fallbackEmail.trim() : "";
    }

    private String resolveEmailVerified(JwtAuthenticationToken authentication) {
        Boolean emailVerified = authentication.getToken().getClaimAsBoolean("email_verified");
        if (emailVerified == null && !claimsNamespace.isBlank()) {
            emailVerified = authentication.getToken().getClaimAsBoolean(claimsNamespace + "email_verified");
        }
        return emailVerified == null ? "" : String.valueOf(emailVerified);
    }
}
