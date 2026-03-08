package com.rumal.payment_service.service;

import com.rumal.payment_service.exception.DuplicateResourceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PaymentInitiationLockService {

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;
    private final String keyPrefix;
    private final DefaultRedisScript<Long> releaseScript;

    public PaymentInitiationLockService(
            StringRedisTemplate redisTemplate,
            @Value("${payment.initiation-lock.ttl:15s}") Duration lockTtl,
            @Value("${payment.initiation-lock.key-prefix:payment:initiate:order:}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.lockTtl = lockTtl == null ? Duration.ofSeconds(15) : lockTtl;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "payment:initiate:order:";
        this.releaseScript = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class
        );
    }

    public LockHandle acquire(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        String key = keyPrefix + orderId;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, lockTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new DuplicateResourceException("Payment initiation is already in progress for this order. Retry in a few seconds.");
        }
        return new LockHandle(key, token);
    }

    public void release(LockHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            redisTemplate.execute(releaseScript, List.of(handle.key()), handle.token());
        } catch (Exception ex) {
            log.warn("Failed to release payment initiation lock {}", handle.key(), ex);
        }
    }

    public record LockHandle(String key, String token) {
    }
}
