package com.rumal.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class RequestBodySizeLimitFilter implements GlobalFilter, Ordered {

    private final long maxBodyBytes;

    public RequestBodySizeLimitFilter(
            @Value("${gateway.max-request-body-size:2MB}") DataSize maxBodySize
    ) {
        this.maxBodyBytes = maxBodySize.toBytes();
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > maxBodyBytes) {
            return writePayloadTooLarge(exchange);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> writePayloadTooLarge(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        String body = "{\"timestamp\":\"" + Instant.now() + "\"," +
                "\"path\":\"" + exchange.getRequest().getPath().value() + "\"," +
                "\"status\":413," +
                "\"error\":\"Payload Too Large\"," +
                "\"message\":\"Request body exceeds the maximum allowed size\"," +
                "\"requestId\":\"" + (requestId != null ? requestId : "") + "\"}";
        exchange.getResponse().setStatusCode(HttpStatus.CONTENT_TOO_LARGE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
