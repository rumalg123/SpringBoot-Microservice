package com.rumal.api_gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RequestBodySizeLimitFilter implements GlobalFilter, Ordered {

    private final long maxBodyBytes;
    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(
            @Value("${gateway.max-request-body-size:2MB}") DataSize maxBodySize,
            ObjectMapper objectMapper
    ) {
        this.maxBodyBytes = maxBodySize.toBytes();
        this.objectMapper = objectMapper;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (contentLength > maxBodyBytes) {
            return writePayloadTooLarge(exchange);
        }
        // For chunked requests (contentLength == -1), enforce via streaming wrapper
        if (contentLength < 0) {
            ServerHttpRequest decorated = new SizeLimitingRequestDecorator(exchange.getRequest(), maxBodyBytes);
            return chain.filter(exchange.mutate().request(decorated).build());
        }
        return chain.filter(exchange);
    }

    private static class SizeLimitingRequestDecorator extends ServerHttpRequestDecorator {
        private final long maxBytes;

        SizeLimitingRequestDecorator(ServerHttpRequest delegate, long maxBytes) {
            super(delegate);
            this.maxBytes = maxBytes;
        }

        @Override
        @NonNull
        public Flux<DataBuffer> getBody() {
            AtomicLong bytesRead = new AtomicLong(0);
            return super.getBody().doOnNext(buffer -> {
                long total = bytesRead.addAndGet(buffer.readableByteCount());
                if (total > maxBytes) {
                    DataBufferUtils.release(buffer);
                    throw new PayloadTooLargeException("Request body exceeds the maximum allowed size");
                }
            });
        }
    }

    static class PayloadTooLargeException extends RuntimeException {
        PayloadTooLargeException(String message) {
            super(message);
        }
    }

    private Mono<Void> writePayloadTooLarge(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", exchange.getRequest().getPath().value());
        body.put("status", 413);
        body.put("error", "Payload Too Large");
        body.put("message", "Request body exceeds the maximum allowed size");
        body.put("requestId", requestId != null ? requestId : "");

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"status\":413,\"error\":\"Payload Too Large\"}".getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(HttpStatus.CONTENT_TOO_LARGE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
