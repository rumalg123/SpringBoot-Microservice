package com.rumal.api_gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IpFilterConfig implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IpFilterConfig.class);

    private final Set<String> blockedIps;
    private final Set<String> allowedIps;
    private final boolean allowlistEnabled;
    private final TrustedProxyResolver trustedProxyResolver;
    private final ObjectMapper objectMapper;

    public IpFilterConfig(
            @Value("${gateway.ip-filter.blocked:}") String blockedIps,
            @Value("${gateway.ip-filter.allowed:}") String allowedIps,
            @Value("${gateway.ip-filter.allowlist-enabled:false}") boolean allowlistEnabled,
            TrustedProxyResolver trustedProxyResolver,
            ObjectMapper objectMapper
    ) {
        this.blockedIps = parseIpSet(blockedIps);
        this.allowedIps = parseIpSet(allowedIps);
        this.allowlistEnabled = allowlistEnabled;
        this.trustedProxyResolver = trustedProxyResolver;
        this.objectMapper = objectMapper;
        if (allowlistEnabled && this.allowedIps.isEmpty()) {
            log.warn("IP allowlist enabled but no IPs configured — blocking all traffic");
        }
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        if (blockedIps.isEmpty() && !allowlistEnabled) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);

        if (!blockedIps.isEmpty() && blockedIps.contains(clientIp)) {
            log.warn("Blocked request from IP={} path={}", clientIp, exchange.getRequest().getPath().value());
            return writeForbidden(exchange);
        }

        if (allowlistEnabled && (allowedIps.isEmpty() || !allowedIps.contains(clientIp))) {
            log.warn("IP not in allowlist IP={} path={}", clientIp, exchange.getRequest().getPath().value());
            return writeForbidden(exchange);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("status", 403);
        body.put("error", "Forbidden");
        body.put("message", "Access denied");

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"status\":403,\"error\":\"Forbidden\"}".getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        return trustedProxyResolver.resolveClientIp(exchange);
    }

    private Set<String> parseIpSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
