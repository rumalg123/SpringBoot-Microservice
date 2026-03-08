package com.rumal.api_gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.api_gateway.service.SessionHandleResolver;
import com.rumal.api_gateway.service.TokenRevocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RevokedTokenFilter implements GlobalFilter, Ordered {

    private final TokenRevocationService tokenRevocationService;
    private final SessionHandleResolver sessionHandleResolver;
    private final TrustedProxyResolver trustedProxyResolver;
    private final ObjectMapper objectMapper;
    private final boolean failOpenOnRedisError;

    public RevokedTokenFilter(
            TokenRevocationService tokenRevocationService,
            SessionHandleResolver sessionHandleResolver,
            TrustedProxyResolver trustedProxyResolver,
            ObjectMapper objectMapper,
            @Value("${auth.revoked-token.fail-open-on-error:false}") boolean failOpenOnRedisError
    ) {
        this.tokenRevocationService = tokenRevocationService;
        this.sessionHandleResolver = sessionHandleResolver;
        this.trustedProxyResolver = trustedProxyResolver;
        this.objectMapper = objectMapper;
        this.failOpenOnRedisError = failOpenOnRedisError;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return chain.filter(exchange);
        }

        String rawToken = authHeader.substring(7).trim();
        if (!StringUtils.hasText(rawToken)) {
            return chain.filter(exchange);
        }

        Map<String, Object> unverifiedClaims = sessionHandleResolver.parseUnverifiedClaims(rawToken);
        String subject = sessionHandleResolver.extractSubject(unverifiedClaims);
        String sessionHandle = sessionHandleResolver.extractSessionHandle(unverifiedClaims);
        String clientIp = trustedProxyResolver.resolveClientIp(exchange);
        String userAgent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);

        return tokenRevocationService.isTokenRevoked(rawToken)
                .flatMap(revoked -> {
                    if (Boolean.TRUE.equals(revoked) && !isLogoutReplay(exchange)) {
                        return writeUnauthorized(exchange, "Access token has been revoked");
                    }
                    if (isLogoutReplay(exchange)) {
                        return chain.filter(exchange);
                    }
                    if (isSessionSync(exchange)) {
                        return Mono.zip(
                                        tokenRevocationService.isSubjectRevoked(subject),
                                        tokenRevocationService.isSessionHandleRevoked(sessionHandle)
                                )
                                .flatMap(revocationState -> {
                                    if (Boolean.TRUE.equals(revocationState.getT1()) || Boolean.TRUE.equals(revocationState.getT2())) {
                                        return writeUnauthorized(exchange, "Session is no longer valid");
                                    }
                                    return chain.filter(exchange);
                                });
                    }
                    return tokenRevocationService.validateActiveSession(sessionHandle, subject, clientIp, userAgent)
                            .flatMap(result -> {
                                if (result.isAllowed()) {
                                    return chain.filter(exchange);
                                }
                                return writeUnauthorized(exchange, messageFor(result));
                            });
                })
                .onErrorResume(error -> failOpenOnRedisError
                        ? chain.filter(exchange)
                        : writeRedisUnavailable(exchange));
    }

    private boolean isLogoutReplay(ServerWebExchange exchange) {
        return exchange.getRequest().getMethod() == HttpMethod.POST
                && "/auth/logout".equals(exchange.getRequest().getPath().value());
    }

    private boolean isSessionSync(ServerWebExchange exchange) {
        return exchange.getRequest().getMethod() == HttpMethod.POST
                && "/auth/session".equals(exchange.getRequest().getPath().value());
    }

    private String messageFor(TokenRevocationService.SessionValidationResult result) {
        return switch (result.status()) {
            case ACTIVE -> "Access granted";
            case FINGERPRINT_MISMATCH -> "Session fingerprint mismatch";
            case MISSING -> "Session is not registered";
            case REVOKED -> "Session is no longer valid";
        };
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        Map<String, Object> body = buildBody(exchange, 401, "Unauthorized", message);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"invalid_token\", error_description=\"" + message + "\"");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(toJson(body, 401));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> writeRedisUnavailable(ServerWebExchange exchange) {
        Map<String, Object> body = buildBody(exchange, 503, "Service Unavailable", "Token revocation service unavailable");
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(toJson(body, 503));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Map<String, Object> buildBody(ServerWebExchange exchange, int status, String error, String message) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("requestId", requestId != null ? requestId : "");
        return body;
    }

    private byte[] toJson(Map<String, Object> body, int fallbackStatus) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            return ("{\"status\":" + fallbackStatus + ",\"error\":\"Internal Error\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 4;
    }
}
