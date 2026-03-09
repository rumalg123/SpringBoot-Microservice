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
import java.util.regex.Pattern;

public abstract class AbstractRedisServletIdempotencyFilter extends OncePerRequestFilter {
    private static final String PENDING_PREFIX = "PENDING|";
    private static final String DONE_PREFIX = "DONE|";
    private static final String REQUEST_HASH = "requestHash";
    private static final String STATUS = "status";
    private static final String CONTENT_TYPE = "contentType";
    private static final String BODY_BASE64 = "bodyBase64";
    private static final String CREATED_AT = "createdAt";
    private static final String PAYLOAD_MISMATCH_MESSAGE = "Same idempotency key cannot be used with a different request payload";
    private static final String REQUEST_STILL_PROCESSING_MESSAGE = "Request with the same idempotency key is still processing";
    private static final int DEFAULT_MAX_CACHED_REQUEST_BODY_BYTES = 256 * 1024;
    private static final int DEFAULT_MAX_CACHED_RESPONSE_BODY_BYTES = 512 * 1024;
    private static final Pattern IDEM_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{1,128}$");

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
        if (handleMissingKey(rawIdemKey, response)) {
            return;
        }
        if (bypassOversizedRequestBody(request, response, filterChain)) {
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String idemKey = rawIdemKey.trim();
        if (handleInvalidKey(idemKey, response)) {
            return;
        }
        String reqHash = buildRequestHash(wrappedRequest);
        String redisKey = buildRedisKey(wrappedRequest, idemKey);

        try {
            if (handleExistingState(response, redisKey, reqHash)) {
                return;
            }
            if (!acquireOrRespondConflict(response, redisKey, reqHash)) {
                return;
            }
            processRequest(wrappedRequest, response, filterChain, redisKey, reqHash);
        } catch (IdempotencyStateUnavailableException ex) {
            safeDelete(redisKey);
            writeUnavailable(response);
        }
    }

    private boolean handleMissingKey(String rawIdemKey, HttpServletResponse response) throws IOException {
        if (StringUtils.hasText(rawIdemKey)) {
            return false;
        }
        writeMissingKey(response);
        return true;
    }

    private boolean bypassOversizedRequestBody(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isOversizedRequestBody(request)) {
            return false;
        }
        response.setHeader("X-Idempotent-Bypass", "request-body-too-large");
        filterChain.doFilter(request, response);
        return true;
    }

    private boolean handleInvalidKey(String idemKey, HttpServletResponse response) throws IOException {
        if (IDEM_KEY_PATTERN.matcher(idemKey).matches()) {
            return false;
        }
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Invalid Idempotency-Key format\"}");
        return true;
    }

    private boolean handleExistingState(HttpServletResponse response, String redisKey, String reqHash) throws IOException {
        String current = get(redisKey);
        if (!StringUtils.hasText(current)) {
            return false;
        }
        return replayExistingState(response, current, reqHash);
    }

    private boolean replayExistingState(HttpServletResponse response, String state, String reqHash) throws IOException {
        if (state.startsWith(DONE_PREFIX)) {
            replay(response, state.substring(DONE_PREFIX.length()), reqHash);
            return true;
        }
        if (!state.startsWith(PENDING_PREFIX)) {
            return false;
        }
        if (isPendingHashMismatch(state.substring(PENDING_PREFIX.length()), reqHash)) {
            writePayloadMismatch(response);
            return true;
        }
        writeConflict(response, REQUEST_STILL_PROCESSING_MESSAGE);
        return true;
    }

    private boolean acquireOrRespondConflict(HttpServletResponse response, String redisKey, String reqHash) throws IOException {
        if (acquire(redisKey, reqHash)) {
            return true;
        }
        String existing = get(redisKey);
        if (StringUtils.hasText(existing) && existing.startsWith(DONE_PREFIX)) {
            replay(response, existing.substring(DONE_PREFIX.length()), reqHash);
            return false;
        }
        if (isPendingMismatch(existing, reqHash)) {
            writePayloadMismatch(response);
            return false;
        }
        writeConflict(response, REQUEST_STILL_PROCESSING_MESSAGE);
        return false;
    }

    private boolean isPendingMismatch(String existing, String reqHash) {
        return StringUtils.hasText(existing)
                && existing.startsWith(PENDING_PREFIX)
                && isPendingHashMismatch(existing.substring(PENDING_PREFIX.length()), reqHash);
    }

    private void processRequest(
            CachedBodyHttpServletRequest wrappedRequest,
            HttpServletResponse response,
            FilterChain filterChain,
            String redisKey,
            String reqHash
    ) throws ServletException, IOException {
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            finalizeResponse(redisKey, reqHash, wrappedResponse);
        } catch (Exception ex) {
            safeDelete(redisKey);
            throw ex;
        }
    }

    private void finalizeResponse(String redisKey, String reqHash, ContentCachingResponseWrapper wrappedResponse) throws IOException {
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
            throw new IdempotencyStateUnavailableException("Redis acquire failed for key " + key, ex);
        }
    }

    private String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            log.warn("{} Redis lookup failed (fail-closed) for key {}", logName(), key, ex);
            throw new IdempotencyStateUnavailableException("Redis lookup failed for key " + key, ex);
        }
    }

    private void storeDone(String key, String reqHash, ContentCachingResponseWrapper response) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(REQUEST_HASH, reqHash);
            payload.put(STATUS, response.getStatus());
            payload.put(CONTENT_TYPE, response.getContentType());
            payload.put(BODY_BASE64, Base64.getEncoder().encodeToString(response.getContentAsByteArray()));
            redisTemplate.opsForValue().set(key, DONE_PREFIX + objectMapper.writeValueAsString(payload), responseTtl);
        } catch (Exception ex) {
            log.warn("{} Redis completion write failed (fail-closed) for key {}", logName(), key, ex);
            throw new IdempotencyStateUnavailableException("Redis completion write failed for key " + key, ex);
        }
    }

    private void replay(HttpServletResponse response, String json, String reqHash) throws IOException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            String storedHash = (String) payload.get(REQUEST_HASH);
            if (StringUtils.hasText(storedHash) && !storedHash.equals(reqHash)) {
                writePayloadMismatch(response);
                return;
            }
            response.setStatus(((Number) payload.getOrDefault(STATUS, 200)).intValue());
            String contentType = (String) payload.get(CONTENT_TYPE);
            response.setContentType(StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("X-Idempotent-Replay", "true");
            byte[] body = Base64.getDecoder().decode((String) payload.getOrDefault(BODY_BASE64, ""));
            response.getOutputStream().write(body);
        } catch (Exception ex) {
            writeConflict(response, "Unable to replay idempotent response. Retry with a new key.");
        }
    }

    private String pendingPayload(String reqHash) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(REQUEST_HASH, reqHash);
        m.put(CREATED_AT, System.currentTimeMillis());
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            return "{\"" + REQUEST_HASH + "\":\"" + escape(reqHash) + "\"}";
        }
    }

    private boolean isPendingHashMismatch(String json, String reqHash) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(json, Map.class);
            Object stored = payload.get(REQUEST_HASH);
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

    private void writePayloadMismatch(HttpServletResponse response) throws IOException {
        writeConflict(response, PAYLOAD_MISMATCH_MESSAGE);
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
        return getClass().getSimpleName();
    }

    private static final class IdempotencyStateUnavailableException extends RuntimeException {
        private IdempotencyStateUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        private byte[] getCachedBody() {
            return cachedBody.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return byteArrayInputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return byteArrayInputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // No async IO for cached bodies.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
