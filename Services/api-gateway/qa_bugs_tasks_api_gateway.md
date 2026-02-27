# API Gateway - Deep Audit Report

## Service Summary

| Attribute | Value |
|-----------|-------|
| Framework | Spring Boot 4.0.3 + Spring Cloud Gateway (WebFlux) |
| Auth | Keycloak JWT (OAuth2 Resource Server) |
| State | Stateless (Redis for rate limiting + idempotency) |
| Routing | 45+ routes via application.yaml, all with circuit breakers |
| Filters | 8 GlobalFilters (IP filter, body limit, idempotency, rate limit, auth relay, etc.) |

---

## BUG-GW-001: Keycloak Admin Token Never Refreshes After Initial Fetch

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Logic / Runtime |
| **File** | `src/main/java/com/rumal/api_gateway/service/KeycloakManagementService.java` |
| **Lines** | 72-117 |

### Description

The token caching mechanism has a fatal logic error. `refreshToken()` (line 80) creates `MonoA` — a cached wrapper around `fetchAccessToken()` — and stores it in `cachedTokenMono`. But when `MonoA` is subscribed, `fetchAccessToken()` (line 113-115) **overwrites** `cachedTokenMono` with `MonoB` (`Mono.just(token).cache(cacheDuration)`).

From that point on, `getAccessToken()` (line 73) always returns `MonoB`. When `MonoB`'s cache duration expires, it resubscribes to its source — `Mono.just(token)` — which always emits the **same stale token**. No refresh is ever triggered.

**Result**: After the Keycloak admin token's TTL expires (typically 300s), **all calls to `resendVerificationEmail` permanently fail** with `BAD_GATEWAY` until the gateway is restarted. The error from Keycloak doesn't clear the cached stale token because the `doOnError` handler is on `MonoA` (which nobody references anymore), not on `MonoB`.

### Execution Trace

```
1. getAccessToken() → cachedTokenMono is null → refreshToken()
2. refreshToken() creates MonoA, sets cachedTokenMono = MonoA
3. MonoA subscribes → fetchAccessToken() runs → creates MonoB, sets cachedTokenMono = MonoB (overwrites!)
4. Subsequent calls: getAccessToken() → cachedTokenMono = MonoB → stale token forever
5. Token expires → Keycloak returns 401 → BAD_GATEWAY to client → no refresh triggered
```

### Fix

Replace the dual-caching mechanism with a single coherent caching strategy. Remove the inner `cachedTokenMono.set()` inside `fetchAccessToken()` and rely solely on the `refreshToken()` Mono cache with proper TTL derived from the token response.

```java
// KeycloakManagementService.java — replace lines 72-117

private Mono<String> getAccessToken() {
    Mono<String> cached = cachedTokenMono.get();
    if (cached != null) {
        return cached;
    }
    return refreshToken();
}

private Mono<String> refreshToken() {
    Mono<String> tokenMono = fetchAccessToken()
            .cacheInvalidateWhen(token -> Mono.delay(tokenTtl(token)).then())
            .doOnError(e -> cachedTokenMono.set(null));
    if (cachedTokenMono.compareAndSet(null, tokenMono)
            || cachedTokenMono.compareAndSet(cachedTokenMono.get(), tokenMono)) {
        return tokenMono;
    }
    // Another thread won the race, use their Mono
    return cachedTokenMono.get();
}

private Mono<String> fetchAccessToken() {
    return webClient.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", adminRealm)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData("client_id", adminClientId)
                    .with("client_secret", adminClientSecret)
                    .with("grant_type", "client_credentials"))
            .retrieve()
            .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(body -> Mono.error(new ResponseStatusException(
                                    HttpStatus.BAD_GATEWAY,
                                    "Keycloak token request failed"))))
            .bodyToMono(KeycloakAccessTokenResponse.class)
            .flatMap(resp -> {
                String token = resp.accessToken();
                if (!StringUtils.hasText(token)) {
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Keycloak access token is empty"));
                }
                return Mono.just(token);
            });
}
```

Alternatively, a simpler approach using a time-based refresh with `AtomicReference`:

```java
// Replace the entire caching mechanism with:

private record CachedToken(String token, Instant expiresAt) {}
private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

private Mono<String> getAccessToken() {
    CachedToken cached = cachedToken.get();
    if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
        return Mono.just(cached.token());
    }
    return fetchAccessToken();
}

// In fetchAccessToken(), after getting the token:
private Mono<String> fetchAccessToken() {
    return webClient.post()
            // ... same request ...
            .bodyToMono(KeycloakAccessTokenResponse.class)
            .flatMap(resp -> {
                String token = resp.accessToken();
                if (!StringUtils.hasText(token)) {
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY, "Keycloak access token is empty"));
                }
                long ttl = resp.expiresIn() > 0 ? resp.expiresIn() : 300;
                Instant expiresAt = Instant.now().plusSeconds(
                        Math.max(1, ttl - TOKEN_REFRESH_MARGIN.getSeconds()));
                cachedToken.set(new CachedToken(token, expiresAt));
                return Mono.just(token);
            });
}
```

---

## BUG-GW-002: Payment Rate Limiter Beans Defined but Never Wired

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Architecture / Security |
| **File** | `src/main/java/com/rumal/api_gateway/config/RateLimitConfig.java` (lines 286-299) |
| **File** | `src/main/java/com/rumal/api_gateway/config/RateLimitEnforcementFilter.java` (lines 226-338) |

### Description

`RateLimitConfig` defines two payment-specific rate limiters:
- `paymentMeRateLimiter` (8 req/s, burst 16) — line 286
- `paymentMeWriteRateLimiter` (4 req/s, burst 8) — line 294

However, `RateLimitEnforcementFilter` **never injects these beans** and has **no route matching** for `/payments/me/**` or `/payments/vendor/me/**` in `resolvePolicy()`. These endpoints fall through to the **default** rate limiter (15 req/s, burst 30).

**Result**: Payment endpoints have ~2-4x looser rate limits than intended. This enables abuse scenarios:
- A compromised account could trigger many payment operations before being throttled.
- Webhook endpoints (`/webhooks/**`) also use default limits — legitimate PayHere callbacks could be rate-limited during high-volume payment processing.

### Fix

Add the missing rate limiter injections and route matching.

```java
// RateLimitEnforcementFilter.java — add to constructor parameters (after line 96):

@Qualifier("paymentMeRateLimiter") RedisRateLimiter paymentMeRateLimiter,
@Qualifier("paymentMeWriteRateLimiter") RedisRateLimiter paymentMeWriteRateLimiter,

// Add corresponding fields and assignments.

// Add to resolvePolicy() method, before the final default return (before line 338):

if (("/payments/me".equals(path) || path.startsWith("/payments/me/"))
        && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
    return new Policy("payment-me-read", paymentMeRateLimiter, userOrIpKeyResolver);
}
if (("/payments/me".equals(path) || path.startsWith("/payments/me/"))
        && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE)) {
    return new Policy("payment-me-write", paymentMeWriteRateLimiter, userOrIpKeyResolver);
}
if ("/payments/vendor/me".equals(path) || path.startsWith("/payments/vendor/me/")) {
    if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
        return new Policy("payment-vendor-me-read", paymentMeRateLimiter, userOrIpKeyResolver);
    }
    return new Policy("payment-vendor-me-write", paymentMeWriteRateLimiter, userOrIpKeyResolver);
}
```

---

## BUG-GW-003: IdempotencyFilter Unbounded Response Body Buffering

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Architecture / Resilience |
| **File** | `src/main/java/com/rumal/api_gateway/config/IdempotencyFilter.java` |
| **Lines** | 205, 229 |

### Description

In `forwardAndCapture()`, the entire downstream response body is buffered into a `ByteArrayOutputStream` (line 205) with no size limit. The `RequestBodySizeLimitFilter` limits **request** bodies to 2MB, but there is no corresponding limit on **response** bodies.

If a downstream service returns a large response (e.g., a paginated admin order list, a product export), the entire body is held in JVM heap memory for the duration of the Redis write. Under high concurrency with idempotency keys, this could cause memory pressure or OOM.

This only affects mutating requests that carry an `Idempotency-Key` header, limiting the blast radius — but admin bulk operations and checkout flows are precisely the endpoints where this applies.

### Fix

Add a response body size cap. If the response exceeds the cap, skip caching but still return the response.

```java
// IdempotencyFilter.java — in forwardAndCapture(), around line 205:

private static final long MAX_CACHEABLE_RESPONSE_SIZE = 512 * 1024; // 512KB

// In the ServerHttpResponseDecorator.writeWith() method (line 222), replace:
@Override
public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
    snapshot();
    Flux<DataBuffer> bodyFlux = Flux.from(body)
            .map(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                if (responseBodyCapture.size() + bytes.length <= MAX_CACHEABLE_RESPONSE_SIZE) {
                    responseBodyCapture.writeBytes(bytes);
                } else {
                    responseTooLarge.set(true);
                }
                return bufferFactory().wrap(bytes);
            });
    return super.writeWith(bodyFlux);
}

// Then in the .then() block after chain.filter(), check responseTooLarge
// and delete the Redis key instead of persisting:
.then(Mono.defer(() -> {
    if (responseTooLarge.get()) {
        return redisTemplate.delete(redisKey).then();
    }
    return persistCompletedEntry(/* ... */);
}))
```

---

## BUG-GW-004: Duplicated Trusted Proxy IP Resolution Logic — Divergence Risk

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Architecture / Security |
| **File** | `src/main/java/com/rumal/api_gateway/config/RateLimitConfig.java` (lines 320-397) |
| **File** | `src/main/java/com/rumal/api_gateway/config/TrustedProxyResolver.java` (lines 41-118) |

### Description

Two independent, copy-pasted implementations of the same IP resolution logic exist:
1. `TrustedProxyResolver` — used by `IpFilterConfig` (IP blocking) and `AccessLoggingFilter` (logging)
2. `RateLimitConfig` (private methods + inner `CidrRange` record) — used by `ipKeyResolver` and `userOrIpKeyResolver` (rate limiting)

Both parse the same `RATE_LIMIT_TRUSTED_PROXY_IPS` config, parse CIDR ranges, check `CF-Connecting-IP` then `X-Forwarded-For[0]`. The logic is identical **today**, but maintaining two copies creates a guaranteed drift risk.

**Scenario**: If `TrustedProxyResolver` is updated to support a new proxy header (e.g., `True-Client-IP`) but `RateLimitConfig` is not, an attacker could be correctly identified by the IP filter but mis-identified by the rate limiter — potentially bypassing rate limits by sending a spoofed `CF-Connecting-IP` from a non-trusted proxy.

### Fix

Delete the private IP resolution methods and `CidrRange` record from `RateLimitConfig` and inject `TrustedProxyResolver` instead.

```java
// RateLimitConfig.java — replace the class:

@Configuration
public class RateLimitConfig {

    private final TrustedProxyResolver trustedProxyResolver;

    public RateLimitConfig(TrustedProxyResolver trustedProxyResolver) {
        this.trustedProxyResolver = trustedProxyResolver;
    }

    // ... all @Bean methods stay the same ...

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> "sub:" + auth.getToken().getSubject())
                .switchIfEmpty(reactor.core.publisher.Mono.just(
                        "ip:" + trustedProxyResolver.resolveClientIp(exchange)));
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> reactor.core.publisher.Mono.just(
                "ip:" + trustedProxyResolver.resolveClientIp(exchange));
    }

    // DELETE: resolveClientIp(), extractRemoteIp(), isTrustedProxy(), CidrRange record
    // DELETE: trustedProxyExactIps, trustedProxyCidrs fields and constructor parsing
}
```

---

## BUG-GW-005: Webhook Endpoints Subject to Default Rate Limiting

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Architecture / Resilience |
| **File** | `src/main/java/com/rumal/api_gateway/config/RateLimitEnforcementFilter.java` |
| **Lines** | 226-338 (resolvePolicy — no `/webhooks/` matching) |

### Description

Webhook endpoints (`/webhooks/payhere`, etc.) have no dedicated rate limit policy in `resolvePolicy()`. They fall through to the default rate limiter (15 req/s, burst 30) keyed by `userOrIpKeyResolver`.

Since webhooks are unauthenticated, the resolver falls back to IP-based keying. If PayHere sends callbacks from a small set of IPs, all callbacks from that IP share one bucket. During high-volume payment processing (flash sales, bulk refunds), legitimate webhook callbacks could be **dropped** with 429 responses.

This is especially dangerous because payment webhooks are typically **not retried indefinitely** — PayHere may retry a few times and then give up, leading to stuck payment states.

### Fix

Either exempt webhooks from rate limiting or create a dedicated generous rate limiter.

```java
// RateLimitEnforcementFilter.java — add to resolvePolicy(), near the top:

if (path.startsWith("/webhooks/")) {
    return new Policy("webhooks", gatewayDefaultRateLimiter, ipKeyResolver);
    // OR: create a webhookRateLimiter with much higher limits (e.g., 100/s burst 200)
}
```

If the PayHere webhook source IPs are known, an even better approach is to exempt those IPs:

```java
// Add a dedicated bean in RateLimitConfig:
@Bean
public RedisRateLimiter webhookRateLimiter(
        @Value("${RATE_LIMIT_WEBHOOK_REPLENISH:100}") int replenishRate,
        @Value("${RATE_LIMIT_WEBHOOK_BURST:200}") int burstCapacity
) {
    return redisRateLimiter(replenishRate, burstCapacity);
}
```

---

## BUG-GW-006: RequestIdFilter Adds Duplicate Header Instead of Replacing

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Data Integrity |
| **File** | `src/main/java/com/rumal/api_gateway/config/RequestIdFilter.java` |
| **Lines** | 27-29 |

### Description

When a valid `X-Request-Id` is received from the client, the filter re-adds it:

```java
ServerHttpRequest request = exchange.getRequest().mutate()
        .header(REQUEST_ID_HEADER, requestId)  // .header() ADDS, doesn't replace
        .build();
```

`ServerHttpRequest.Builder.header()` **appends** the value to existing headers. If the client sends a valid `X-Request-Id: abc-123`, the downstream request will have **two** `X-Request-Id` headers: `abc-123` and `abc-123`. While functionally harmless (downstream `.getFirst()` gets the right value), this is technically incorrect and could confuse middleware that counts header occurrences.

### Fix

```java
// RequestIdFilter.java — replace lines 27-29:

final String resolvedId = requestId;
ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> headers.set(REQUEST_ID_HEADER, resolvedId))
        .build();
```

---

## BUG-GW-007: Incomplete JSON Escaping in Manual JSON Construction

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Security / Data Integrity |
| **Files** | Multiple: `FallbackController.java:36-39`, `IpFilterConfig.java:83-87`, `RateLimitEnforcementFilter.java:346-355`, `RequestBodySizeLimitFilter.java:93-97`, `IdempotencyFilter.java:494-503` |

### Description

Five files contain manual JSON string construction using a custom `escapeJson()` helper. The helper escapes `\`, `"`, `\n`, `\r`, and `\t` — but per the JSON specification (RFC 8259 Section 7), **all** control characters U+0000 through U+001F must be escaped. Missing escapes include:
- `\b` (backspace, U+0008)
- `\f` (form feed, U+000C)
- Null byte (U+0000)
- Other control characters (U+0001–U+0007, U+000B, U+000E–U+001F)

While these characters are extremely unlikely in HTTP paths/headers (most HTTP servers reject them), the manual JSON construction is fragile. Additionally, the `FallbackController` reads from `X-Forwarded-Prefix` which could theoretically contain unusual characters from a misconfigured proxy.

### Fix

Replace all manual JSON construction with Jackson `ObjectMapper`. This also eliminates the code duplication across five files.

```java
// Create a shared utility or use ObjectMapper directly. Example for FallbackController:

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

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "path", path,
                "status", 503,
                "error", "Service Unavailable",
                "message", "The downstream service is temporarily unavailable. Please try again later.",
                "requestId", requestId != null ? requestId : ""
        );

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
```

Apply the same pattern to `IpFilterConfig`, `RateLimitEnforcementFilter`, `RequestBodySizeLimitFilter`, and `IdempotencyFilter`.

---

## BUG-GW-008: AuthController.logout is a No-Op — No Token Revocation

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Security |
| **File** | `src/main/java/com/rumal/api_gateway/controller/AuthController.java` |
| **Lines** | 21-24 |

### Description

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent().build();
}
```

The logout endpoint returns `204 No Content` without performing any server-side action. It does not:
1. Revoke the Keycloak session
2. Revoke the refresh token
3. Add the JWT to a blocklist

A stolen JWT remains valid until its natural expiry. Clients calling this endpoint get a false sense of security — they believe they've logged out, but the token can still be used.

### Fix

Call Keycloak's logout endpoint to revoke the session. This requires the refresh token (which the client should send in the request body) or the session ID from the JWT.

```java
@PostMapping("/logout")
public Mono<ResponseEntity<Void>> logout(JwtAuthenticationToken authentication) {
    String sessionId = authentication.getToken().getClaimAsString("sid");
    if (sessionId == null || sessionId.isBlank()) {
        return Mono.just(ResponseEntity.noContent().build());
    }
    return keycloakManagementService.revokeSession(sessionId)
            .thenReturn(ResponseEntity.noContent().build())
            .onErrorReturn(ResponseEntity.noContent().build());
}
```

Add to `KeycloakManagementService`:

```java
public Mono<Void> revokeSession(String sessionId) {
    return getAccessToken()
            .flatMap(token -> webClient.delete()
                    .uri("/admin/realms/{realm}/sessions/{sessionId}", realm, sessionId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> Mono.empty())
                    .toBodilessEntity()
                    .then());
}
```

---

## BUG-GW-009: Keycloak Error Body Leakage in ResponseStatusException

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Security / Information Disclosure |
| **File** | `src/main/java/com/rumal/api_gateway/service/KeycloakManagementService.java` |
| **Lines** | 62-67, 98-103 |

### Description

Both error handlers in `KeycloakManagementService` include the raw Keycloak error response body in the `ResponseStatusException` message:

```java
Mono.error(new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Keycloak verification email request failed: " + body))  // line 66-67
```

```java
Mono.error(new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Keycloak token request failed: " + body))  // line 102-103
```

Spring WebFlux's default error handling may include the `reason` from `ResponseStatusException` in the HTTP response body. Keycloak error responses can contain internal details such as server version, stack traces, or configuration hints.

### Fix

Log the error body at WARN level but return a generic message to the client.

```java
// Line 62-67:
.onStatus(HttpStatusCode::isError, response ->
        response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .doOnNext(body -> log.warn("Keycloak verification email failed: {}", body))
                .flatMap(body -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Unable to send verification email. Please try again later."))))

// Line 98-103:
.onStatus(HttpStatusCode::isError, response ->
        response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .doOnNext(body -> log.error("Keycloak token request failed: {}", body))
                .flatMap(body -> Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Identity provider unavailable"))))
```

---

## BUG-GW-010: Discovery Locator DenyAll List Requires Manual Sync

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Architecture / Security |
| **File** | `src/main/java/com/rumal/api_gateway/config/SecurityConfig.java` |
| **Line** | 90 |
| **File** | `src/main/resources/application.yaml` |
| **Line** | 508-511 |

### Description

The gateway has a `denyAll()` rule for direct service-name paths:

```java
.pathMatchers("/customer-service/**", "/order-service/**", "/cart-service/**",
        "/wishlist-service/**", "/admin-service/**", "/product-service/**",
        "/poster-service/**", "/vendor-service/**", "/promotion-service/**",
        "/access-service/**", "/payment-service/**", "/inventory-service/**",
        "/discovery-server/**").denyAll()
```

And discovery locator is configurable:
```yaml
discovery:
  locator:
    enabled: ${GATEWAY_DISCOVERY_LOCATOR_ENABLED:false}
```

While disabled by default (good), if enabled, any Eureka-registered service **not** in the denyAll list becomes publicly routable. The denyAll list is missing several services that have routes in the YAML:
- `review-service`
- `analytics-service`
- `personalization-service`
- `search-service`

### Fix

Add the missing service names to the denyAll list:

```java
// SecurityConfig.java — line 90, replace with:
.pathMatchers("/customer-service/**", "/order-service/**", "/cart-service/**",
        "/wishlist-service/**", "/admin-service/**", "/product-service/**",
        "/poster-service/**", "/vendor-service/**", "/promotion-service/**",
        "/access-service/**", "/payment-service/**", "/inventory-service/**",
        "/review-service/**", "/analytics-service/**",
        "/personalization-service/**", "/search-service/**",
        "/discovery-server/**").denyAll()
```

---

## BUG-GW-011: Missing Dedicated Rate Limits for Multiple Endpoint Groups

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Architecture / Resilience |
| **File** | `src/main/java/com/rumal/api_gateway/config/RateLimitEnforcementFilter.java` |
| **Lines** | 226-338 |

### Description

Several endpoint groups in the routing YAML have no dedicated rate limit policy in `resolvePolicy()` and fall through to the default (15 req/s, burst 30):

| Endpoint Pattern | Current Policy | Risk |
|-----------------|----------------|------|
| `/payments/me/**` | default | Payment abuse (see BUG-GW-002) |
| `/payments/vendor/me/**` | default | Payment abuse |
| `/webhooks/**` | default | Legitimate callbacks dropped (see BUG-GW-005) |
| `/admin/promotions/**` | default | Admin endpoint with default limits |
| `/admin/payments/**` | default | Admin endpoint with default limits |
| `/admin/inventory/**` | default | Admin endpoint with default limits |
| `/admin/reviews/**` | default | Admin endpoint with default limits |
| `/admin/api-keys/**` | default | Admin endpoint with default limits |
| `/admin/sessions/**` | default | Admin endpoint with default limits |
| `/admin/permission-groups/**` | default | Admin endpoint with default limits |
| `/admin/dashboard/**` | default | Admin endpoint with default limits |
| `/admin/audit-log/**` | default | Admin endpoint with default limits |
| `/admin/system/**` | default | Admin endpoint with default limits |
| `/reviews/**` | default | Public endpoint, scraping risk |
| `/search/**` | default | Public endpoint, expensive queries |
| `/personalization/**` | default | Multiple sub-paths |
| `/analytics/**` | default | Admin/vendor analytics |

The admin endpoints are authenticated and role-restricted, so the risk is lower. But the public endpoints (`/reviews`, `/search`) and the payment endpoints warrant dedicated limits.

### Fix

Add rate limit policies for at least the public and payment endpoints. For admin endpoints, a generic `admin-*` fallback policy would be sufficient:

```java
// Add to resolvePolicy(), before the final default return:

// Search (expensive)
if ("/search".equals(path) || path.startsWith("/search/")) {
    return new Policy("search-read", productsRateLimiter, ipKeyResolver);
}

// Reviews (public scraping risk)
if (("/reviews".equals(path) || path.startsWith("/reviews/"))
        && (method == HttpMethod.GET || method == HttpMethod.HEAD)) {
    return new Policy("reviews-read", publicCatalogAuxRateLimiter, ipKeyResolver);
}
```

---

## BUG-GW-012: IdempotencyFilter Silently Skips Binary/Multipart Without Logging

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Logic / Observability |
| **File** | `src/main/java/com/rumal/api_gateway/config/IdempotencyFilter.java` |
| **Lines** | 94-97, 387-397 |

### Description

The `shouldSkipBodyBuffering` method returns true for multipart, octet-stream, image, video, and audio content types. When this returns true, the filter's `filter()` method short-circuits to `chain.filter(exchange)` — **completely bypassing idempotency** without any logging or response header indication.

A client sending a `POST /admin/products` with an `Idempotency-Key` header and `Content-Type: multipart/form-data` would receive no idempotency protection and no indication that their key was ignored.

This also means `isProtectedMutation()` routes (which **require** an idempotency key) silently skip enforcement for binary uploads. A duplicate product creation with an image upload would go through twice.

### Fix

At minimum, log when idempotency is skipped and set a response header:

```java
// IdempotencyFilter.java — in filter(), around line 95:

if (!enabled || !isMutatingRequest(exchange) || shouldSkipBodyBuffering(exchange)) {
    if (shouldSkipBodyBuffering(exchange) && isMutatingRequest(exchange)) {
        String idempotencyKey = exchange.getRequest().getHeaders().getFirst(keyHeaderName);
        if (StringUtils.hasText(idempotencyKey)) {
            log.debug("Idempotency skipped for binary/multipart request path={} key={}",
                    exchange.getRequest().getPath().value(), idempotencyKey);
            exchange.getResponse().getHeaders().set("X-Idempotency-Status", "SKIPPED");
        }
    }
    return chain.filter(exchange);
}
```

---

## Audit Summary

| Severity | Count | IDs |
|----------|-------|-----|
| Critical | 0 | — |
| High | 0 | — |
| Medium | 5 | GW-001, GW-002, GW-003, GW-004, GW-005 |
| Low | 7 | GW-006, GW-007, GW-008, GW-009, GW-010, GW-011, GW-012 |
| **Total** | **12** | |

## Positive Observations

The gateway is well-architected overall:

- **Header sanitization** in `AuthHeaderRelayFilter` correctly strips all internal headers (`X-User-Sub`, `X-User-Email`, `X-User-Roles`, `X-Internal-Auth`) before processing, preventing header injection attacks.
- **JWT audience validation** via `AudienceValidator` is thorough — checks `aud`, `resource_access`, and optionally `azp`.
- **Email verification enforcement** is applied to all protected endpoints at the gateway level — a defense-in-depth layer.
- **Circuit breakers** on every single route with proper fallback handling.
- **Idempotency implementation** is sophisticated — Redis-backed with SETNX for race protection, request hash fingerprinting, and cached response replay.
- **Rate limiting** uses per-endpoint policies with read/write differentiation and proper IP/user key resolution.
- **Trusted proxy resolution** correctly checks remote IP before trusting forwarded headers.
- **CORS configuration** is restrictive — specific origins, specific headers, `allow-credentials: false`.
- **Body size limits** protect against both `Content-Length` declared oversize and chunked transfer streaming oversize.
- **Route ordering** in SecurityConfig is correct — specific matchers before catch-all `/admin/**`.
- **Direct service path blocking** (`denyAll()`) prevents discovery locator bypass of security rules.
