package com.rumal.api_gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class TokenRevocationService {

    private static final Duration DEFAULT_REVOKED_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_ACTIVE_SESSION_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_REVOKED_SESSION_TTL = Duration.ofMinutes(15);
    private static final Duration DEFAULT_SUBJECT_INDEX_TTL = Duration.ofDays(30);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String revokedTokenKeyPrefix;
    private final String activeSessionKeyPrefix;
    private final String revokedSessionKeyPrefix;
    private final String revokedSubjectKeyPrefix;
    private final String subjectSessionIndexKeyPrefix;
    private final Duration revokedTokenFallbackTtl;
    private final Duration activeSessionFallbackTtl;
    private final Duration revokedSessionFallbackTtl;
    private final Duration subjectSessionIndexTtl;
    private final boolean enforceClientFingerprint;

    public TokenRevocationService(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${auth.revoked-token.key-prefix:gw:revoked-jwt:v1::}") String revokedTokenKeyPrefix,
            @Value("${auth.active-session.key-prefix:gw:active-session:v1::}") String activeSessionKeyPrefix,
            @Value("${auth.revoked-session.key-prefix:gw:revoked-session:v1::}") String revokedSessionKeyPrefix,
            @Value("${auth.revoked-subject.key-prefix:gw:revoked-subject:v1::}") String revokedSubjectKeyPrefix,
            @Value("${auth.active-session.subject-index-key-prefix:gw:active-session-subject:v1::}") String subjectSessionIndexKeyPrefix,
            @Value("${auth.revoked-token.fallback-ttl:15m}") Duration revokedTokenFallbackTtl,
            @Value("${auth.active-session.fallback-ttl:15m}") Duration activeSessionFallbackTtl,
            @Value("${auth.revoked-session.fallback-ttl:15m}") Duration revokedSessionFallbackTtl,
            @Value("${auth.active-session.subject-index-ttl:30d}") Duration subjectSessionIndexTtl,
            @Value("${auth.active-session.enforce-client-fingerprint:true}") boolean enforceClientFingerprint
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.revokedTokenKeyPrefix = normalizePrefix(revokedTokenKeyPrefix, "gw:revoked-jwt:v1::");
        this.activeSessionKeyPrefix = normalizePrefix(activeSessionKeyPrefix, "gw:active-session:v1::");
        this.revokedSessionKeyPrefix = normalizePrefix(revokedSessionKeyPrefix, "gw:revoked-session:v1::");
        this.revokedSubjectKeyPrefix = normalizePrefix(revokedSubjectKeyPrefix, "gw:revoked-subject:v1::");
        this.subjectSessionIndexKeyPrefix = normalizePrefix(subjectSessionIndexKeyPrefix, "gw:active-session-subject:v1::");
        this.revokedTokenFallbackTtl = normalizeDuration(revokedTokenFallbackTtl, DEFAULT_REVOKED_TOKEN_TTL);
        this.activeSessionFallbackTtl = normalizeDuration(activeSessionFallbackTtl, DEFAULT_ACTIVE_SESSION_TTL);
        this.revokedSessionFallbackTtl = normalizeDuration(revokedSessionFallbackTtl, DEFAULT_REVOKED_SESSION_TTL);
        this.subjectSessionIndexTtl = normalizeDuration(subjectSessionIndexTtl, DEFAULT_SUBJECT_INDEX_TTL);
        this.enforceClientFingerprint = enforceClientFingerprint;
    }

    public Mono<Void> revokeToken(String rawToken, Instant expiresAt) {
        if (!StringUtils.hasText(rawToken)) {
            return Mono.empty();
        }
        Duration ttl = resolveTtl(expiresAt, revokedTokenFallbackTtl);
        if (ttl.isZero() || ttl.isNegative()) {
            return Mono.empty();
        }
        return redisTemplate.opsForValue()
                .set(buildRevokedTokenKey(rawToken), "1", ttl)
                .then();
    }

    public Mono<Boolean> isTokenRevoked(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey(buildRevokedTokenKey(rawToken))
                .defaultIfEmpty(false);
    }

    public Mono<Void> registerActiveSession(
            String sessionHandle,
            String subject,
            Instant expiresAt,
            String clientIp,
            String userAgent
    ) {
        if (!StringUtils.hasText(sessionHandle) || !StringUtils.hasText(subject)) {
            return Mono.empty();
        }
        Duration ttl = resolveTtl(expiresAt, activeSessionFallbackTtl);
        if (ttl.isZero() || ttl.isNegative()) {
            return Mono.empty();
        }

        ActiveSessionSnapshot snapshot = new ActiveSessionSnapshot(
                subject.trim(),
                buildClientFingerprint(clientIp, userAgent)
        );
        String serialized = serializeSnapshot(snapshot);
        String normalizedHandle = sessionHandle.trim();
        String normalizedSubject = subject.trim();

        return isSubjectRevoked(normalizedSubject)
                .flatMap(subjectRevoked -> {
                    boolean subjectAlreadyRevoked = Boolean.TRUE.equals(subjectRevoked);
                    if (subjectAlreadyRevoked) {
                        return Mono.error(new IllegalStateException("Subject session revocation is still active"));
                    }
                    return isSessionHandleRevoked(normalizedHandle)
                            .flatMap(handleRevoked -> {
                                boolean handleAlreadyRevoked = Boolean.TRUE.equals(handleRevoked);
                                if (handleAlreadyRevoked) {
                                    return Mono.error(new IllegalStateException("Session handle has been revoked"));
                                }
                                return redisTemplate.opsForValue()
                                        .set(buildActiveSessionKey(normalizedHandle), serialized, ttl)
                                        .then(redisTemplate.opsForSet().add(buildSubjectIndexKey(normalizedSubject), normalizedHandle))
                                        .then(redisTemplate.expire(buildSubjectIndexKey(normalizedSubject), subjectSessionIndexTtl))
                                        .then();
                            });
                });
    }

    public Mono<SessionValidationResult> validateActiveSession(
            String sessionHandle,
            String subject,
            String clientIp,
            String userAgent
    ) {
        String normalizedSubject = StringUtils.hasText(subject) ? subject.trim() : "";
        return isSubjectRevoked(normalizedSubject)
                .flatMap(subjectRevoked -> {
                    boolean subjectAlreadyRevoked = Boolean.TRUE.equals(subjectRevoked);
                    if (subjectAlreadyRevoked) {
                        return Mono.just(SessionValidationResult.revoked("subject_revoked"));
                    }
                    if (!StringUtils.hasText(sessionHandle)) {
                        return Mono.just(SessionValidationResult.active());
                    }
                    String normalizedHandle = sessionHandle.trim();
                    String expectedFingerprint = buildClientFingerprint(clientIp, userAgent);
                    return isSessionHandleRevoked(normalizedHandle)
                            .flatMap(handleRevoked -> {
                                boolean handleAlreadyRevoked = Boolean.TRUE.equals(handleRevoked);
                                if (handleAlreadyRevoked) {
                                    return Mono.just(SessionValidationResult.revoked("session_revoked"));
                                }
                                return redisTemplate.opsForValue()
                                        .get(buildActiveSessionKey(normalizedHandle))
                                        .flatMap(serialized -> validateSnapshot(serialized, normalizedSubject, expectedFingerprint))
                                        .switchIfEmpty(Mono.just(SessionValidationResult.missing()));
                            });
                });
    }

    public Mono<Boolean> isSessionHandleRevoked(String sessionHandle) {
        if (!StringUtils.hasText(sessionHandle)) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey(buildRevokedSessionKey(sessionHandle.trim()))
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isSubjectRevoked(String subject) {
        if (!StringUtils.hasText(subject)) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey(buildRevokedSubjectKey(subject.trim()))
                .defaultIfEmpty(false);
    }

    public Mono<Void> revokeSessionHandle(String sessionHandle, Instant expiresAt) {
        if (!StringUtils.hasText(sessionHandle)) {
            return Mono.empty();
        }

        String normalizedHandle = sessionHandle.trim();
        Duration ttl = resolveTtl(expiresAt, revokedSessionFallbackTtl);

        return redisTemplate.opsForValue()
                .get(buildActiveSessionKey(normalizedHandle))
                .flatMap(serialized -> {
                    ActiveSessionSnapshot snapshot = deserializeSnapshot(serialized);
                    Mono<Long> removeFromIndex = StringUtils.hasText(snapshot.subject())
                            ? redisTemplate.opsForSet().remove(buildSubjectIndexKey(snapshot.subject()), normalizedHandle)
                            : Mono.just(0L);
                    return removeFromIndex.then();
                })
                .onErrorResume(ignored -> Mono.empty())
                .then(redisTemplate.delete(buildActiveSessionKey(normalizedHandle)).then())
                .then(ttl.isZero() || ttl.isNegative()
                        ? Mono.empty()
                        : redisTemplate.opsForValue().set(buildRevokedSessionKey(normalizedHandle), "1", ttl).then());
    }

    public Mono<Void> revokeAllSessionsForSubject(String subject, Instant expiresAt) {
        if (!StringUtils.hasText(subject)) {
            return Mono.empty();
        }

        String normalizedSubject = subject.trim();
        Duration ttl = resolveTtl(expiresAt, revokedSessionFallbackTtl);
        Mono<Void> revokeKnownHandles = redisTemplate.opsForSet()
                .members(buildSubjectIndexKey(normalizedSubject))
                .collectList()
                .flatMap(handles -> Flux.fromIterable(handles)
                        .concatMap(handle -> revokeSessionHandle(handle, expiresAt))
                        .then());

        Mono<Void> markSubjectRevoked = ttl.isZero() || ttl.isNegative()
                ? Mono.empty()
                : redisTemplate.opsForValue().set(buildRevokedSubjectKey(normalizedSubject), "1", ttl).then();

        return revokeKnownHandles
                .then(markSubjectRevoked)
                .then(redisTemplate.delete(buildSubjectIndexKey(normalizedSubject)).then());
    }

    private Mono<SessionValidationResult> validateSnapshot(
            String serialized,
            String expectedSubject,
            String expectedFingerprint
    ) {
        ActiveSessionSnapshot snapshot = deserializeSnapshot(serialized);
        if (!StringUtils.hasText(snapshot.subject())) {
            return Mono.just(SessionValidationResult.missing());
        }
        if (StringUtils.hasText(expectedSubject) && !snapshot.subject().equals(expectedSubject)) {
            return Mono.just(SessionValidationResult.revoked("subject_mismatch"));
        }
        if (enforceClientFingerprint
                && StringUtils.hasText(snapshot.clientFingerprint())
                && StringUtils.hasText(expectedFingerprint)
                && !snapshot.clientFingerprint().equals(expectedFingerprint)) {
            return Mono.just(SessionValidationResult.fingerprintMismatch());
        }
        return Mono.just(SessionValidationResult.active());
    }

    private String serializeSnapshot(ActiveSessionSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize active session snapshot", e);
        }
    }

    private ActiveSessionSnapshot deserializeSnapshot(String serialized) {
        if (!StringUtils.hasText(serialized)) {
            return new ActiveSessionSnapshot("", "");
        }
        try {
            return objectMapper.readValue(serialized, ActiveSessionSnapshot.class);
        } catch (Exception e) {
            return new ActiveSessionSnapshot("", "");
        }
    }

    private String buildClientFingerprint(String clientIp, String userAgent) {
        String normalizedIpPrefix = normalizeIpPrefix(clientIp);
        String normalizedUserAgent = normalizeUserAgent(userAgent);
        if (!StringUtils.hasText(normalizedIpPrefix) && !StringUtils.hasText(normalizedUserAgent)) {
            return "";
        }
        return sha256Hex(normalizedIpPrefix + "|" + normalizedUserAgent);
    }

    private String normalizeIpPrefix(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "";
        }
        String normalized = clientIp.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') >= 0) {
            List<String> parts = List.of(normalized.split(":"));
            int prefixLength = Math.min(4, parts.size());
            return String.join(":", parts.subList(0, prefixLength));
        }
        String[] parts = normalized.split("\\.");
        if (parts.length < 3) {
            return normalized;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    private String normalizeUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "";
        }
        return userAgent.trim().replaceAll("[\\r\\n]+", " ").toLowerCase(Locale.ROOT);
    }

    private Duration resolveTtl(Instant expiresAt, Duration fallback) {
        if (expiresAt == null) {
            return fallback;
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return Duration.ZERO;
        }
        return remaining;
    }

    private Duration normalizeDuration(Duration candidate, Duration fallback) {
        if (candidate == null || candidate.isNegative() || candidate.isZero()) {
            return fallback;
        }
        return candidate;
    }

    private String normalizePrefix(String candidate, String fallback) {
        return StringUtils.hasText(candidate) ? candidate.trim() : fallback;
    }

    private String buildRevokedTokenKey(String rawToken) {
        return revokedTokenKeyPrefix + sha256Hex(rawToken.trim());
    }

    private String buildActiveSessionKey(String sessionHandle) {
        return activeSessionKeyPrefix + sha256Hex(sessionHandle.trim());
    }

    private String buildRevokedSessionKey(String sessionHandle) {
        return revokedSessionKeyPrefix + sha256Hex(sessionHandle.trim());
    }

    private String buildRevokedSubjectKey(String subject) {
        return revokedSubjectKeyPrefix + sha256Hex(subject.trim());
    }

    private String buildSubjectIndexKey(String subject) {
        return subjectSessionIndexKeyPrefix + sha256Hex(subject.trim());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", e);
        }
    }

    public record ActiveSessionSnapshot(
            String subject,
            String clientFingerprint
    ) {
    }

    public record SessionValidationResult(SessionValidationStatus status, String reason) {

        public static SessionValidationResult active() {
            return new SessionValidationResult(SessionValidationStatus.ACTIVE, "");
        }

        public static SessionValidationResult missing() {
            return new SessionValidationResult(SessionValidationStatus.MISSING, "session_missing");
        }

        public static SessionValidationResult revoked(String reason) {
            return new SessionValidationResult(SessionValidationStatus.REVOKED, reason);
        }

        public static SessionValidationResult fingerprintMismatch() {
            return new SessionValidationResult(SessionValidationStatus.FINGERPRINT_MISMATCH, "client_fingerprint_mismatch");
        }

        public boolean isAllowed() {
            return status == SessionValidationStatus.ACTIVE;
        }
    }

    public enum SessionValidationStatus {
        ACTIVE,
        MISSING,
        REVOKED,
        FINGERPRINT_MISMATCH
    }
}
