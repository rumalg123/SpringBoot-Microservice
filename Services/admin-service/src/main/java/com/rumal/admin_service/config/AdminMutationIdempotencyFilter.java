package com.rumal.admin_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
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

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AdminMutationIdempotencyFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AdminMutationIdempotencyFilter.class);

    private static final String PENDING_PREFIX = "PENDING|";
    private static final String DONE_PREFIX = "DONE|";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Duration pendingTtl;
    private final Duration responseTtl;
    private final String keyHeaderName;
    private final String keyPrefix;

    public AdminMutationIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${admin.idempotency.enabled:true}") boolean enabled,
            @Value("${admin.idempotency.pending-ttl:30s}") Duration pendingTtl,
            @Value("${admin.idempotency.response-ttl:6h}") Duration responseTtl,
            @Value("${admin.idempotency.key-header-name:Idempotency-Key}") String keyHeaderName,
            @Value("${admin.idempotency.key-prefix:admin:idem:v1::}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.pendingTtl = pendingTtl == null ? Duration.ofSeconds(30) : pendingTtl;
        this.responseTtl = responseTtl == null ? Duration.ofHours(6) : responseTtl;
        this.keyHeaderName = StringUtils.hasText(keyHeaderName) ? keyHeaderName : "Idempotency-Key";
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "admin:idem:v1::";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method))) {
            return true;
        }
        String path = normalizePath(request);
        if (!StringUtils.hasText(path) || !path.startsWith("/admin/")) {
            return true;
        }
        if (!StringUtils.hasText(request.getHeader(keyHeaderName))) {
            return true;
        }
        return !isProtectedMutationPath(path, method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String idempotencyKey = request.getHeader(keyHeaderName).trim();
        String requestHash = sha256Hex(wrappedRequest.getCachedBody());
        String redisKey = buildRedisKey(wrappedRequest, idempotencyKey);

        String existing = null;
        try {
            existing = redisTemplate.opsForValue().get(redisKey);
        } catch (Exception ex) {
            log.warn("Admin idempotency Redis lookup failed (fail-open) for key {}", redisKey, ex);
        }
        if (StringUtils.hasText(existing)) {
            if (existing.startsWith(DONE_PREFIX)) {
                replayStoredResponse(response, existing.substring(DONE_PREFIX.length()), requestHash);
                return;
            }
            if (existing.startsWith(PENDING_PREFIX)) {
                if (!matchesPendingHash(existing.substring(PENDING_PREFIX.length()), requestHash)) {
                    writeConflict(response, "Same idempotency key cannot be used with a different request payload");
                    return;
                }
                writeConflict(response, "Request with the same idempotency key is still processing");
                return;
            }
        }

        boolean acquired = false;
        try {
            Boolean set = redisTemplate.opsForValue().setIfAbsent(
                    redisKey,
                    PENDING_PREFIX + serializePending(requestHash),
                    pendingTtl
            );
            acquired = Boolean.TRUE.equals(set);
        } catch (Exception ex) {
            log.warn("Admin idempotency Redis acquire failed (fail-open) for key {}", redisKey, ex);
        }

        if (!acquired) {
            String current;
            try {
                current = redisTemplate.opsForValue().get(redisKey);
            } catch (Exception ex) {
                log.warn("Admin idempotency Redis lookup after acquire miss failed (fail-open) for key {}", redisKey, ex);
                current = null;
            }
            if (StringUtils.hasText(current) && current.startsWith(DONE_PREFIX)) {
                replayStoredResponse(response, current.substring(DONE_PREFIX.length()), requestHash);
                return;
            }
            if (StringUtils.hasText(current) && current.startsWith(PENDING_PREFIX)
                    && !matchesPendingHash(current.substring(PENDING_PREFIX.length()), requestHash)) {
                writeConflict(response, "Same idempotency key cannot be used with a different request payload");
                return;
            }
            writeConflict(response, "Request with the same idempotency key is still processing");
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrapped);

            int status = wrapped.getStatus();
            if (status >= 500) {
                safeDelete(redisKey);
                wrapped.copyBodyToResponse();
                return;
            }

            byte[] bodyBytes = wrapped.getContentAsByteArray();
            String bodyBase64 = Base64.getEncoder().encodeToString(bodyBytes == null ? new byte[0] : bodyBytes);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestHash", requestHash);
            payload.put("status", status);
            payload.put("contentType", wrapped.getContentType());
            payload.put("bodyBase64", bodyBase64);
            try {
                redisTemplate.opsForValue().set(redisKey, DONE_PREFIX + objectMapper.writeValueAsString(payload), responseTtl);
            } catch (Exception ex) {
                log.warn("Admin idempotency Redis completion write failed (fail-open) for key {}", redisKey, ex);
            }
            wrapped.copyBodyToResponse();
        } catch (Exception ex) {
            safeDelete(redisKey);
            throw ex;
        }
    }

    private boolean isProtectedMutationPath(String path, String method) {
        if ("PATCH".equalsIgnoreCase(method)) {
            return path.matches("^/admin/orders/[^/]+/status$")
                    || path.matches("^/admin/orders/vendor-orders/[^/]+/status$");
        }
        if ("POST".equalsIgnoreCase(method)) {
            return path.matches("^/admin/vendors/[^/]+/(stop-orders|resume-orders|delete-request|confirm-delete|restore)$")
                    || path.matches("^/admin/vendors/[^/]+/users/onboard$")
                    || path.matches("^/admin/vendors/[^/]+/users$")
                    || path.matches("^/admin/posters(/.*)?$")
                    || path.matches("^/admin/platform-staff(/.*)?$")
                    || path.matches("^/admin/vendor-staff(/.*)?$");
        }
        if ("PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            if (path.matches("^/admin/vendors/[^/]+/users/[^/]+$")) {
                return true;
            }
            return path.matches("^/admin/posters(/.*)?$")
                    || path.matches("^/admin/platform-staff(/.*)?$")
                    || path.matches("^/admin/vendor-staff(/.*)?$");
        }
        return false;
    }

    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return "";
        }
        String context = request.getContextPath();
        if (StringUtils.hasText(context) && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    private String buildRedisKey(HttpServletRequest request, String idempotencyKey) {
        String actor = request.getHeader("X-User-Sub");
        String scope = StringUtils.hasText(actor) ? actor.trim() : "anonymous";
        String basis = request.getMethod() + "|" + normalizePath(request) + "|" + idempotencyKey;
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(basis.getBytes(StandardCharsets.UTF_8));
        return keyPrefix + scope + "::" + encoded;
    }

    private void replayStoredResponse(HttpServletResponse response, String json, String requestHash) throws IOException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            String storedRequestHash = (String) payload.get("requestHash");
            if (StringUtils.hasText(storedRequestHash) && !storedRequestHash.equals(requestHash)) {
                writeConflict(response, "Same idempotency key cannot be used with a different request payload");
                return;
            }
            int status = ((Number) payload.getOrDefault("status", 200)).intValue();
            String contentType = (String) payload.get("contentType");
            String bodyBase64 = (String) payload.getOrDefault("bodyBase64", "");
            byte[] body = Base64.getDecoder().decode(bodyBase64);

            response.setStatus(status);
            if (StringUtils.hasText(contentType)) {
                response.setContentType(contentType);
            } else {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            }
            response.setHeader("X-Idempotent-Replay", "true");
            response.getOutputStream().write(body);
        } catch (JsonProcessingException ex) {
            writeConflict(response, "Unable to replay idempotent response. Retry with a new key.");
        }
    }

    private String serializePending(String requestHash) {
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("requestHash", requestHash);
        pending.put("createdAt", System.currentTimeMillis());
        try {
            return objectMapper.writeValueAsString(pending);
        } catch (JsonProcessingException ex) {
            return "{\"requestHash\":\"" + escapeJson(requestHash) + "\",\"createdAt\":" + System.currentTimeMillis() + "}";
        }
    }

    private boolean matchesPendingHash(String pendingJson, String requestHash) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(pendingJson, Map.class);
            Object stored = payload.get("requestHash");
            return stored == null || String.valueOf(stored).equals(requestHash);
        } catch (Exception ex) {
            return true;
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "sha256-unavailable";
        }
    }

    private void writeConflict(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"error\":\"" + escapeJson(message) + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void safeDelete(String redisKey) {
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception ex) {
            log.debug("Admin idempotency Redis delete cleanup failed for key {}", redisKey, ex);
        }
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
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
                    // sync request body wrapper; no async callback support needed here
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
