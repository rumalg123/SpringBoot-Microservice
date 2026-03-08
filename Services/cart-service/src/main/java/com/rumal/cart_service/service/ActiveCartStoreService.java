package com.rumal.cart_service.service;

import com.rumal.cart_service.exception.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ActiveCartStoreService {

    private static final Logger log = LoggerFactory.getLogger(ActiveCartStoreService.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cartTtl;
    private final Duration lockTtl;

    public ActiveCartStoreService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${cart.expiry.ttl:30d}") Duration cartTtl,
            @Value("${cart.lock.ttl:5s}") Duration lockTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cartTtl = cartTtl;
        this.lockTtl = lockTtl;
    }

    public Optional<ActiveCartState> loadCustomerCart(String keycloakId) {
        return load(cartKey("customer", keycloakId));
    }

    public Optional<ActiveCartState> loadGuestCart(String guestCartId) {
        return load(cartKey("guest", guestCartId));
    }

    public void saveCustomerCart(String keycloakId, ActiveCartState cart) {
        save(cartKey("customer", keycloakId), cart);
    }

    public void saveGuestCart(String guestCartId, ActiveCartState cart) {
        save(cartKey("guest", guestCartId), cart);
    }

    public void deleteGuestCart(String guestCartId) {
        redisTemplate.delete(cartKey("guest", guestCartId));
    }

    public <T> T withCustomerCartLock(String keycloakId, Supplier<T> callback) {
        return withLock(lockKey("customer", keycloakId), callback);
    }

    public <T> T withGuestCartLock(String guestCartId, Supplier<T> callback) {
        return withLock(lockKey("guest", guestCartId), callback);
    }

    private Optional<ActiveCartState> load(String key) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            ActiveCartState cart = objectMapper.readValue(raw, ActiveCartState.class);
            redisTemplate.expire(key, cartTtl);
            return Optional.of(cart);
        } catch (JsonProcessingException ex) {
            log.warn("Deleting unreadable Redis cart state for key={}", key, ex);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    private void save(String key, ActiveCartState cart) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(cart), cartTtl);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Unable to persist cart state");
        }
    }

    private <T> T withLock(String key, Supplier<T> callback) {
        String token = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 20; attempt++) {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, token, lockTtl);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    return callback.get();
                } finally {
                    try {
                        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(key), token);
                    } catch (RuntimeException ex) {
                        log.warn("Failed releasing Redis cart lock for key={}", key, ex);
                    }
                }
            }
            try {
                Thread.sleep(25L * (attempt + 1));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ValidationException("Cart is busy. Please retry.");
            }
        }
        throw new ValidationException("Cart is busy. Please retry.");
    }

    private String cartKey(String namespace, String id) {
        return "cart:active:" + namespace + ":" + id.trim();
    }

    private String lockKey(String namespace, String id) {
        return "cart:lock:" + namespace + ":" + id.trim();
    }
}
