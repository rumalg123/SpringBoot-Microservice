package com.rumal.api_gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class FallbackController {

    private final ObjectMapper objectMapper;

    public FallbackController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RequestMapping("/fallback/unavailable")
    public Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String originalPath = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Prefix");
        String path = originalPath != null ? originalPath : exchange.getRequest().getPath().value();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", path);
        body.put("status", 503);
        body.put("error", "Service Unavailable");
        body.put("message", "The downstream service is temporarily unavailable. Please try again later.");
        body.put("requestId", requestId != null ? requestId : "");

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"status\":503,\"error\":\"Service Unavailable\"}".getBytes(StandardCharsets.UTF_8);
        }

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", "30");
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }
}
