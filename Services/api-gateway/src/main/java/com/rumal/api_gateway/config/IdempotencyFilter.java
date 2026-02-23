package com.rumal.api_gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Component
@NullMarked
public class IdempotencyFilter implements GlobalFilter, Ordered {

    private static final Set<String> EXCLUDED_RESPONSE_HEADERS = Set.of(
            HttpHeaders.CONTENT_LENGTH.toLowerCase(),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(),
            HttpHeaders.CONNECTION.toLowerCase(),
            "keep-alive",
            HttpHeaders.DATE.toLowerCase(),
            HttpHeaders.SERVER.toLowerCase()
    );

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KeyResolver userOrIpKeyResolver;
    private final boolean enabled;
    private final boolean requireKeyForMutatingRequests;
    private final Duration responseTtl;
    private final Duration pendingTtl;
    private final String keyHeaderName;
    private final String keyPrefix;

    public IdempotencyFilter(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Qualifier("userOrIpKeyResolver") KeyResolver userOrIpKeyResolver,
            @Value("${idempotency.enabled:true}") boolean enabled,
            @Value("${idempotency.require-key-for-mutating-requests:false}") boolean requireKeyForMutatingRequests,
            @Value("${idempotency.response-ttl:24h}") Duration responseTtl,
            @Value("${idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${idempotency.key-prefix:gw:idem:v1::}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userOrIpKeyResolver = userOrIpKeyResolver;
        this.enabled = enabled;
        this.requireKeyForMutatingRequests = requireKeyForMutatingRequests;
        this.responseTtl = responseTtl;
        this.pendingTtl = pendingTtl;
        this.keyHeaderName = keyHeaderName;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled || !isMutatingRequest(exchange) || isMultipartRequest(exchange)) {
            return chain.filter(exchange);
        }

        boolean keyRequired = requireKeyForMutatingRequests || isProtectedMutation(exchange);
        String idempotencyKey = exchange.getRequest().getHeaders().getFirst(keyHeaderName);
        if (!StringUtils.hasText(idempotencyKey)) {
            if (!keyRequired) {
                return chain.filter(exchange);
            }
            return writeJsonError(exchange, HttpStatus.BAD_REQUEST, "Idempotency-Key header is required", "MISSING_KEY");
        }

        return readRequestBody(exchange)
                .flatMap(requestBody -> userOrIpKeyResolver.resolve(exchange)
                        .defaultIfEmpty("ip:unknown")
                        .map(this::normalizeScopeKey)
                        .flatMap(scopeKey -> applyIdempotency(exchange, chain, scopeKey, idempotencyKey.trim(), requestBody)));
    }

    private Mono<Void> applyIdempotency(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String scopeKey,
            String idempotencyKey,
            byte[] requestBody
    ) {
        String requestPath = exchange.getRequest().getPath().value();
        String requestQuery = exchange.getRequest().getURI().getRawQuery();
        String method = exchange.getRequest().getMethod().name();
        String requestHash = sha256Hex(method + "|" + requestPath + "|" + (requestQuery == null ? "" : requestQuery) + "|"
                + Base64.getEncoder().encodeToString(requestBody));
        String redisKey = buildRedisKey(scopeKey, method, requestPath, idempotencyKey);

        return redisTemplate.opsForValue().get(redisKey)
                .flatMap(existing -> handleExistingEntry(exchange, redisKey, existing, requestHash).thenReturn(Boolean.TRUE))
                .switchIfEmpty(
                        Mono.defer(() -> startPendingAndForward(exchange, chain, redisKey, requestHash, requestBody)
                                .thenReturn(Boolean.TRUE))
                )
                .then();
    }

    private Mono<Void> handleExistingEntry(
            ServerWebExchange exchange,
            String redisKey,
            String rawEntry,
            String requestHash
    ) {
        IdempotencyEntry entry = deserializeEntry(rawEntry);
        if (entry == null) {
            return redisTemplate.delete(redisKey)
                    .then(writeJsonError(exchange, HttpStatus.CONFLICT, "Idempotency key state was invalid. Retry with a new key."));
        }

        if (!requestHash.equals(entry.requestHash())) {
            return writeJsonError(exchange, HttpStatus.CONFLICT, "Same idempotency key cannot be used with a different request payload");
        }

        if (entry.isCompleted()) {
            return writeCachedResponse(exchange, entry);
        }

        if (entry.isPending()) {
            return writeJsonError(exchange, HttpStatus.CONFLICT, "Request with the same idempotency key is still processing");
        }

        return redisTemplate.delete(redisKey)
                .then(writeJsonError(exchange, HttpStatus.CONFLICT, "Unknown idempotency state. Retry with a new key."));
    }

    private Mono<Void> startPendingAndForward(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String redisKey,
            String requestHash,
            byte[] requestBody
    ) {
        String pendingJson = serializeEntry(IdempotencyEntry.pending(requestHash));
        if (pendingJson == null) {
            return writeJsonError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to initialize idempotency state", "ERROR");
        }

        return redisTemplate.opsForValue().setIfAbsent(redisKey, pendingJson, pendingTtl)
                .flatMap(acquired -> {
                    if (acquired) {
                        return forwardAndCapture(exchange, chain, redisKey, requestHash, requestBody);
                    }
                    return redisTemplate.opsForValue().get(redisKey)
                            .flatMap(existing -> handleExistingEntry(exchange, redisKey, existing, requestHash).thenReturn(Boolean.TRUE))
                            .switchIfEmpty(
                                    Mono.defer(() -> writeJsonError(exchange, HttpStatus.CONFLICT, "Request is already in progress")
                                            .thenReturn(Boolean.TRUE))
                            )
                            .then();
                });
    }

    private Mono<Void> forwardAndCapture(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String redisKey,
            String requestHash,
            byte[] requestBody
    ) {
        ServerHttpRequest decoratedRequest = buildDecoratedRequest(exchange, requestBody);
        ByteArrayOutputStream responseBodyCapture = new ByteArrayOutputStream();
        AtomicReference<@Nullable HttpStatusCode> responseStatusRef = new AtomicReference<>();
        AtomicReference<HttpHeaders> responseHeadersRef = new AtomicReference<>(new HttpHeaders());

        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {

            private void snapshot() {
                HttpStatusCode statusCode = getStatusCode();
                if (statusCode != null) {
                    responseStatusRef.set(statusCode);
                }
                HttpHeaders snapshot = new HttpHeaders();
                snapshot.putAll(getHeaders());
                responseHeadersRef.set(snapshot);
            }

            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                snapshot();
                Flux<DataBuffer> bodyFlux = Flux.from(body)
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            responseBodyCapture.writeBytes(bytes);
                            return bufferFactory().wrap(bytes);
                        });
                return super.writeWith(bodyFlux);
            }

            @Override
            public Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(inner -> inner));
            }

            @Override
            public Mono<Void> setComplete() {
                snapshot();
                return super.setComplete();
            }
        };

        ServerWebExchange decoratedExchange = exchange.mutate()
                .request(decoratedRequest)
                .response(decoratedResponse)
                .build();
        decoratedExchange.getResponse().getHeaders().set("X-Idempotency-Status", "MISS");

        return chain.filter(decoratedExchange)
                .onErrorResume(error -> redisTemplate.delete(redisKey).then(Mono.error(error)))
                .then(Mono.defer(() -> persistCompletedEntry(
                        redisKey,
                        requestHash,
                        responseStatusRef.get(),
                        responseHeadersRef.get(),
                        responseBodyCapture.toByteArray()
                )).onErrorResume(error -> redisTemplate.delete(redisKey).then()));
    }

    private Mono<Void> persistCompletedEntry(
            String redisKey,
            String requestHash,
            @Nullable HttpStatusCode statusCode,
            @Nullable HttpHeaders responseHeaders,
            byte[] responseBody
    ) {
        int status = statusCode == null ? HttpStatus.OK.value() : statusCode.value();
        if (status >= 500) {
            return redisTemplate.delete(redisKey).then();
        }

        Map<String, List<String>> headersToStore = new LinkedHashMap<>();
        if (responseHeaders != null) {
            responseHeaders.forEach((name, values) -> {
                if (!EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                    headersToStore.put(name, new ArrayList<>(values));
                }
            });
        }

        IdempotencyEntry completed = IdempotencyEntry.completed(
                requestHash,
                status,
                headersToStore,
                Base64.getEncoder().encodeToString(responseBody)
        );
        String completedJson = serializeEntry(completed);
        if (completedJson == null) {
            return redisTemplate.delete(redisKey).then();
        }
        return redisTemplate.opsForValue().set(redisKey, completedJson, responseTtl).then();
    }

    private Mono<Void> writeCachedResponse(ServerWebExchange exchange, IdempotencyEntry entry) {
        HttpStatusCode statusCode = HttpStatusCode.valueOf(entry.statusCode() == null ? HttpStatus.OK.value() : entry.statusCode());
        exchange.getResponse().setStatusCode(statusCode);
        exchange.getResponse().getHeaders().set("X-Idempotency-Status", "HIT");

        entry.headers().forEach((name, values) -> {
            if (!EXCLUDED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                exchange.getResponse().getHeaders().put(name, new ArrayList<>(values));
            }
        });

        byte[] responseBytes = decodeBase64(entry.bodyBase64());
        if (responseBytes.length == 0) {
            return exchange.getResponse().setComplete();
        }

        DataBuffer dataBuffer = exchange.getResponse().bufferFactory().wrap(responseBytes);
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private Mono<Void> writeJsonError(ServerWebExchange exchange, HttpStatus status, String message) {
        return writeJsonError(exchange, status, message, "CONFLICT");
    }

    private Mono<Void> writeJsonError(ServerWebExchange exchange, HttpStatus status, String message, String idempotencyStatus) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        String body = "{\"timestamp\":\"" + Instant.now() + "\"," +
                "\"path\":\"" + exchange.getRequest().getPath().value() + "\"," +
                "\"status\":" + status.value() + "," +
                "\"error\":\"" + status.getReasonPhrase() + "\"," +
                "\"message\":\"" + escapeJson(message) + "\"," +
                "\"requestId\":\"" + escapeJson(requestId == null ? "" : requestId) + "\"}";
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("X-Idempotency-Status", idempotencyStatus);
        DataBuffer dataBuffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private Mono<byte[]> readRequestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .switchIfEmpty(Mono.just(new byte[0]));
    }

    private ServerHttpRequest buildDecoratedRequest(ServerWebExchange exchange, byte[] body) {
        return new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(HttpHeaders.CONTENT_LENGTH);
                headers.setContentLength(body.length);
                return headers;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> {
                    byte[] cloned = body.clone();
                    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(cloned);
                    return Mono.just(buffer);
                });
            }
        };
    }

    private boolean isMutatingRequest(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;
    }

    private boolean isMultipartRequest(ServerWebExchange exchange) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        return contentType != null && MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType);
    }

    private boolean isProtectedMutation(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == null) {
            return false;
        }
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/admin/")) {
            return true;
        }
        if ("/orders/me".equals(path) && method == HttpMethod.POST) {
            return true;
        }
        if (("/customers/me/addresses".equals(path) || path.startsWith("/customers/me/addresses/"))
                && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)) {
            return true;
        }
        if ("/customers/me".equals(path) && method == HttpMethod.PUT) {
            return true;
        }
        if ("/cart/me/checkout".equals(path) && method == HttpMethod.POST) {
            return true;
        }
        if (("/cart/me".equals(path) || "/cart/me/items".equals(path) || path.startsWith("/cart/me/items/"))
                && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)) {
            return true;
        }
        if (("/wishlist/me".equals(path) || "/wishlist/me/items".equals(path) || path.startsWith("/wishlist/me/items/"))
                && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)) {
            return true;
        }
        return false;
    }

    private String buildRedisKey(String scopeKey, String method, String path, String idempotencyKey) {
        String encodedIdempotencyKey = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(idempotencyKey.getBytes(StandardCharsets.UTF_8));
        return keyPrefix + scopeKey + "::" + method + "::" + path + "::" + encodedIdempotencyKey;
    }

    private String normalizeScopeKey(String scopeKey) {
        if (!StringUtils.hasText(scopeKey)) {
            return "ip:unknown";
        }
        return scopeKey.trim();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", e);
        }
    }

    private @Nullable String serializeEntry(IdempotencyEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private @Nullable IdempotencyEntry deserializeEntry(String raw) {
        try {
            return objectMapper.readValue(raw, IdempotencyEntry.class);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] decodeBase64(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private String escapeJson(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private record IdempotencyEntry(
            String state,
            String requestHash,
            @Nullable Integer statusCode,
            Map<String, List<String>> headers,
            @Nullable String bodyBase64,
            String createdAt
    ) {
        static IdempotencyEntry pending(String requestHash) {
            return new IdempotencyEntry("PENDING", requestHash, null, Map.of(), null, Instant.now().toString());
        }

        static IdempotencyEntry completed(
                String requestHash,
                Integer statusCode,
                Map<String, List<String>> headers,
                String bodyBase64
        ) {
            return new IdempotencyEntry("COMPLETED", requestHash, statusCode, headers, bodyBase64, Instant.now().toString());
        }

        boolean isPending() {
            return "PENDING".equalsIgnoreCase(state);
        }

        boolean isCompleted() {
            return "COMPLETED".equalsIgnoreCase(state);
        }
    }
}
