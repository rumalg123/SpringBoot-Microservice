package com.rumal.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/unavailable")
    public Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String originalPath = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Prefix");
        String path = originalPath != null ? originalPath : exchange.getRequest().getPath().value();

        String body = "{\"timestamp\":\"" + Instant.now() + "\"," +
                "\"path\":\"" + escapeJson(path) + "\"," +
                "\"status\":503," +
                "\"error\":\"Service Unavailable\"," +
                "\"message\":\"The downstream service is temporarily unavailable. Please try again later.\"," +
                "\"requestId\":\"" + escapeJson(requestId) + "\"}";

        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", "30");
        var dataBuffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
