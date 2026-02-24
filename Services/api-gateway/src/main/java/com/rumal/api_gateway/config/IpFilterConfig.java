package com.rumal.api_gateway.config;

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

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class IpFilterConfig implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(IpFilterConfig.class);

    private final Set<String> blockedIps;
    private final Set<String> allowedIps;
    private final boolean allowlistEnabled;

    public IpFilterConfig(
            @Value("${gateway.ip-filter.blocked:}") String blockedIps,
            @Value("${gateway.ip-filter.allowed:}") String allowedIps,
            @Value("${gateway.ip-filter.allowlist-enabled:false}") boolean allowlistEnabled
    ) {
        this.blockedIps = parseIpSet(blockedIps);
        this.allowedIps = parseIpSet(allowedIps);
        this.allowlistEnabled = allowlistEnabled;
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

        if (allowlistEnabled && !allowedIps.isEmpty() && !allowedIps.contains(clientIp)) {
            log.warn("IP not in allowlist IP={} path={}", clientIp, exchange.getRequest().getPath().value());
            return writeForbidden(exchange);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange) {
        String body = "{\"timestamp\":\"" + Instant.now() + "\"," +
                "\"path\":\"" + exchange.getRequest().getPath().value() + "\"," +
                "\"status\":403," +
                "\"error\":\"Forbidden\"," +
                "\"message\":\"Access denied\"}";
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String cfIp = exchange.getRequest().getHeaders().getFirst("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
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
