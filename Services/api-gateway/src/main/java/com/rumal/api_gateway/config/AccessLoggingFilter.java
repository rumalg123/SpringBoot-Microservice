package com.rumal.api_gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Component
public class AccessLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessLoggingFilter.class);
    private final TrustedProxyResolver trustedProxyResolver;

    public AccessLoggingFilter(TrustedProxyResolver trustedProxyResolver) {
        this.trustedProxyResolver = trustedProxyResolver;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        Instant start = Instant.now();
        ServerHttpRequest request = exchange.getRequest();

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> logAccess(exchange, request, start)));
    }

    private void logAccess(ServerWebExchange exchange, ServerHttpRequest request, Instant start) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int statusCode = status != null ? status.value() : 0;
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();
        String query = request.getURI().getRawQuery();
        String requestId = request.getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        String userSub = request.getHeaders().getFirst("X-User-Sub");
        String clientIp = resolveClientIp(exchange);
        org.springframework.cloud.gateway.route.Route route = exchange.getAttribute(
                org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unknown";

        if (statusCode >= 500) {
            log.error("ACCESS method={} path={} query={} status={} durationMs={} routeId={} requestId={} user={} ip={}",
                    method, path, query, statusCode, durationMs, routeId, requestId, userSub, clientIp);
        } else if (statusCode >= 400) {
            log.warn("ACCESS method={} path={} query={} status={} durationMs={} routeId={} requestId={} user={} ip={}",
                    method, path, query, statusCode, durationMs, routeId, requestId, userSub, clientIp);
        } else {
            log.info("ACCESS method={} path={} query={} status={} durationMs={} routeId={} requestId={} user={} ip={}",
                    method, path, query, statusCode, durationMs, routeId, requestId, userSub, clientIp);
        }
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        return trustedProxyResolver.resolveClientIp(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
