package com.rumal.shared.idempotency.servlet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

public abstract class AbstractRedisServletIdempotencyFilter extends OncePerRequestFilter {
    private static final String PENDING_PREFIX = "PENDING|";
    private static final String DONE_PREFIX = "DONE|";
    private static final int DEFAULT_MAX_CACHED_REQUEST_BODY_BYTES = 256 * 1024;
    private static final int DEFAULT_MAX_CACHED_RESPONSE_BODY_BYTES = 512 * 1024;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration pendingTtl;
    private final Duration responseTtl;
    private final String keyHeaderName;
    private final String keyPrefix;
    private final Logger log = LoggerFactory.getLogger(getClass());

    protected AbstractRedisServletIdempotencyFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Duration pendingTtl,
            Duration responseTtl,
            String keyHeaderName,
            String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.pendingTtl = pendingTtl == null ? Duration.ofSeconds(30) : pendingTtl;
        this.responseTtl = responseTtl == null ? Duration.ofHours(24) : responseTtl;
        this.keyHeaderName = StringUtils.hasText(keyHeaderName) ? keyHeaderName : "Idempotency-Key";
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "idem:v1::";
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!isFilterEnabled()) {
            return true;
        }
        String method = request.getMethod();
        if (!supportsMethod(method)) {
            return true;
        }
        String path = normalizePath(request);
        return !isProtectedMutationPath(path, method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawIdemKey = request.getHeader(keyHeaderName);
        if (!StringUtils.hasText(rawIdemKey)) {
            writeMissingKey(response);
            return;
        }
        if (isOversizedRequestBody(request)) {
            response.setHeader("X-Idempotent-Bypass", "request-body-too-large");
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String idemKey = rawIdemKey.trim();
        String reqHash = buildRequestHash(wrappedRequest);
        String redisKey = buildRedisKey(wrappedRequest, idemKey);

        try {
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
                if (StringUtils.hasText(existing)
                        && existing.startsWith(PENDING_PREFIX)
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
                if (isOversizedResponseBody(wrappedResponse)) {
                    safeDelete(redisKey);
                    wrappedResponse.setHeader("X-Idempotent-Bypass", "response-body-too-large");
                    wrappedResponse.copyBodyToResponse();
                    return;
                }
                storeDone(redisKey, reqHash, wrappedResponse);
                wrappedResponse.copyBodyToResponse();
            } catch (Exception ex) {
                safeDelete(redisKey);
                throw ex;
            }
        } catch (IdempotencyStateUnavailableException ex) {
            safeDelete(redisKey);
            writeUnavailable(response);
        }
    }

    protected abstract boolean isFilterEnabled();

    protected abstract boolean isProtectedMutationPath(String path, String method);

    protected boolean supportsMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    protected int maxCachedRequestBodyBytes() {
        return DEFAULT_MAX_CACHED_REQUEST_BODY_BYTES;
    }

    protected int maxCachedResponseBodyBytes() {
        return DEFAULT_MAX_CACHED_RESPONSE_BODY_BYTES;
    }

    protected String anonymousActorScope() {
        return "anonymous";
    }

    protected String normalizePath(HttpServletRequest request) {
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

    protected String buildRedisKey(HttpServletRequest request, String idemKey) {
        String actor = request.getHeader("X-User-Sub");
        String scope = StringUtils.hasText(actor) ? actor.trim() : anonymousActorScope();
        String basis = request.getMethod() + "|" + normalizePath(request) + "|" + idemKey;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(basis.getBytes(StandardCharsets.UTF_8));
        return keyPrefix + scope + "::" + encoded;
    }

    private String buildRequestHash(CachedBodyHttpServletRequest request) {
        String method = request.getMethod();
        String path = normalizePath(request);
        String query = request.getQueryString();
        String basis = (method == null ? "" : method)
                + "|"
                + (path == null ? "" : path)
                + "|"
                + (query == null ? "" : query)
                + "|"
                + Base64.getEncoder().encodeToString(request.getCachedBody());
        return sha256Hex(basis.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isOversizedRequestBody(HttpServletRequest request) {
        int maxBytes = maxCachedRequestBodyBytes();
        if (maxBytes <= 0) {
            return false;
        }
        long contentLength = request.getContentLengthLong();
        return contentLength > maxBytes;
    }

    private boolean isOversizedResponseBody(ContentCachingResponseWrapper response) {
        int maxBytes = maxCachedResponseBodyBytes();
        if (maxBytes <= 0) {
            return false;
        }
        return response.getContentAsByteArray().length > maxBytes;
    }

    private boolean acquire(String key, String reqHash) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, PENDING_PREFIX + pendingPayload(reqHash), pendingTtl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception ex) {
            log.warn("{} Redis acquire failed (fail-closed) for key {}", logName(), key, ex);
            throw new IdempotencyStateUnavailableException(ex);
        }
    }

    private String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("{} Redis lookup failed (fail-closed) for key {}", logName(), key, ex);
            throw new IdempotencyStateUnavailableException(ex);
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
            log.warn("{} Redis completion write failed (fail-closed) for key {}", logName(), key, ex);
            throw new IdempotencyStateUnavailableException(ex);
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
            log.debug("{} Redis delete cleanup failed for key {}", logName(), key, ex);
        }
    }

    private void writeConflict(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.CONFLICT.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write(("{\"error\":\"" + escape(message) + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    private void writeMissingKey(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write("{\"error\":\"Idempotency-Key header is required\"}".getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getOutputStream().write("{\"error\":\"Idempotency service unavailable. Retry later.\"}".getBytes(StandardCharsets.UTF_8));
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
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String logName() {
        String simpleName = getClass().getSimpleName();
        if (simpleName.endsWith("Filter")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Filter".length());
        }
        return simpleName;
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

    private static final class IdempotencyStateUnavailableException extends RuntimeException {
        private IdempotencyStateUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
