package com.rumal.order_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OrderMutationIdempotencyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(OrderMutationIdempotencyFilter.class);

    private static final String PENDING_PREFIX = "PENDING|";
    private static final String DONE_PREFIX = "DONE|";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration pendingTtl;
    private final Duration responseTtl;
    private final String keyHeaderName;
    private final String keyPrefix;

    public OrderMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${order.idempotency.enabled:true}") boolean enabled,
            @Value("${order.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${order.idempotency.response-ttl:24h}") Duration responseTtl,
            @Value("${order.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${order.idempotency.key-prefix:os:idem:v1::}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.pendingTtl = pendingTtl == null ? Duration.ofSeconds(30) : pendingTtl;
        this.responseTtl = responseTtl == null ? Duration.ofHours(24) : responseTtl;
        this.keyHeaderName = StringUtils.hasText(keyHeaderName) ? keyHeaderName : "Idempotency-Key";
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "os:idem:v1::";
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!enabled) return true;
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))) return true;
        String path = normalizePath(request);
        if (!isProtectedMutationPath(path, method)) return true;
        return !StringUtils.hasText(request.getHeader(keyHeaderName));
    }

    private boolean isProtectedMutationPath(String path, String method) {
        if ("POST".equalsIgnoreCase(method)) {
            return "/orders".equals(path) || "/orders/me".equals(path);
        }
        if (!"PATCH".equalsIgnoreCase(method)) {
            return false;
        }
        return (path.startsWith("/orders/") && path.endsWith("/status"))
                || (path.startsWith("/orders/vendor-orders/") && path.endsWith("/status"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    )
            throws ServletException, IOException {
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String idemKey = request.getHeader(keyHeaderName).trim();
        String reqHash = sha256Hex(wrappedRequest.getCachedBody());
        String redisKey = buildRedisKey(wrappedRequest, idemKey);

        String current = get(redisKey);
        if (StringUtils.hasText(current)) {
            if (current.startsWith(DONE_PREFIX)) {
                replay(response, current.substring(DONE_PREFIX.length()), reqHash);
                return;
            }
            if (current.startsWith(PENDING_PREFIX)) {
                if (isPendingHashMismatch(current.substring(PENDING_PREFIX.length()), reqHash)) {
                    writeConflict(response, "Same idempotency key cannot be used with a different request payload");
                    return;
                }
                writeConflict(response, "Request with the same idempotency key is still processing");
                return;
            }
        }

        if (!acquire(redisKey, reqHash)) {
            String existing = get(redisKey);
            if (StringUtils.hasText(existing) && existing.startsWith(DONE_PREFIX)) {
                replay(response, existing.substring(DONE_PREFIX.length()), reqHash);
                return;
            }
            if (StringUtils.hasText(existing) && existing.startsWith(PENDING_PREFIX)
                    && isPendingHashMismatch(existing.substring(PENDING_PREFIX.length()), reqHash)) {
                writeConflict(response, "Same idempotency key cannot be used with a different request payload");
                return;
            }
            writeConflict(response, "Request with the same idempotency key is still processing");
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            int status = wrappedResponse.getStatus();
            if (status >= 500) {
                safeDelete(redisKey);
                wrappedResponse.copyBodyToResponse();
                return;
            }
            storeDone(redisKey, reqHash, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        } catch (Exception ex) {
            safeDelete(redisKey);
            throw ex;
        }
    }

    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (StringUtils.hasText(context) && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private String buildRedisKey(HttpServletRequest request, String idemKey) {
        String actor = request.getHeader("X-User-Sub");
        String scope = StringUtils.hasText(actor) ? actor.trim() : "anonymous";
        String path = normalizePath(request);
        String basis = request.getMethod() + "|" + path + "|" + idemKey;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(basis.getBytes(StandardCharsets.UTF_8));
        return keyPrefix + scope + "::" + encoded;
    }

    private boolean acquire(String key, String reqHash) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, PENDING_PREFIX + pendingPayload(reqHash), pendingTtl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception ex) {
            log.warn("Order idempotency Redis acquire failed (fail-open) for key {}", key, ex);
            return true; // fail-open
        }
    }

    private String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("Order idempotency Redis lookup failed (fail-open) for key {}", key, ex);
            return null;
        }
    }

    private void storeDone(String key, String reqHash, ContentCachingResponseWrapper response) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestHash", reqHash);
            payload.put("status", response.getStatus());
            payload.put("contentType", response.getContentType());
            payload.put("bodyBase64", Base64.getEncoder().encodeToString(response.getContentAsByteArray()));
            redisTemplate.opsForValue().set(key, DONE_PREFIX + objectMapper.writeValueAsString(payload), responseTtl);
        } catch (Exception ex) {
            log.warn("Order idempotency Redis completion write failed (fail-open) for key {}", key, ex);
        }
    }

    private void replay(HttpServletResponse response, String json, String reqHash) throws IOException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            String storedHash = (String) payload.get("requestHash");
            if (StringUtils.hasText(storedHash) && !storedHash.equals(reqHash)) {
                writeConflict(response, "Same idempotency key cannot be used with a different request payload");
                return;
            }
            response.setStatus(((Number) payload.getOrDefault("status", 200)).intValue());
            String contentType = (String) payload.get("contentType");
            response.setContentType(StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-Idempotent-Replay", "true");
            byte[] body = Base64.getDecoder().decode((String) payload.getOrDefault("bodyBase64", ""));
            response.getOutputStream().write(body);
        } catch (Exception ex) {
            writeConflict(response, "Unable to replay idempotent response. Retry with a new key.");
        }
    }

    private String pendingPayload(String reqHash) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestHash", reqHash);
        m.put("createdAt", System.currentTimeMillis());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            return "{\"requestHash\":\"" + escape(reqHash) + "\"}";
        }
    }

    private boolean isPendingHashMismatch(String json, String reqHash) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            Object stored = payload.get("requestHash");
            return stored != null && !reqHash.equals(String.valueOf(stored));
        } catch (Exception ex) {
            return false;
        }
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.debug("Order idempotency Redis delete cleanup failed for key {}", key, ex);
        }
    }

    private void writeConflict(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(("{\"error\":\"" + escape(message) + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Hex(byte[] body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(body == null ? new byte[0] : body);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (Exception ex) {
            return "sha256-unavailable";
        }
    }

    private String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        private byte[] getCachedBody() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
