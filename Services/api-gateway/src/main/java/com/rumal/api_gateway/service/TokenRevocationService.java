package com.rumal.api_gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class TokenRevocationService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration fallbackTtl;

    public TokenRevocationService(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${auth.revoked-token.key-prefix:gw:revoked-jwt:v1::}") String keyPrefix,
            @Value("${auth.revoked-token.fallback-ttl:15m}") Duration fallbackTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "gw:revoked-jwt:v1::";
        this.fallbackTtl = fallbackTtl == null || fallbackTtl.isNegative() || fallbackTtl.isZero()
                ? Duration.ofMinutes(15)
                : fallbackTtl;
    }

    public Mono<Void> revokeToken(String rawToken, Instant expiresAt) {
        if (!StringUtils.hasText(rawToken)) {
            return Mono.empty();
        }

        Duration ttl = resolveTtl(expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return Mono.empty();
        }

        return redisTemplate.opsForValue()
                .set(buildRedisKey(rawToken), "1", ttl)
                .then();
    }

    public Mono<Boolean> isTokenRevoked(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return Mono.just(false);
        }
        return redisTemplate.hasKey(buildRedisKey(rawToken))
                .defaultIfEmpty(false);
    }

    private Duration resolveTtl(Instant expiresAt) {
        if (expiresAt == null) {
            return fallbackTtl;
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            return Duration.ZERO;
        }
        return remaining;
    }

    private String buildRedisKey(String rawToken) {
        return keyPrefix + sha256Hex(rawToken.trim());
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm is unavailable", e);
        }
    }
}
