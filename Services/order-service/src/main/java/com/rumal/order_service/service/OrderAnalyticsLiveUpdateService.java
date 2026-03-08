package com.rumal.order_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.order_service.dto.AnalyticsLiveDashboardMessage;
import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.VendorOrder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderAnalyticsLiveUpdateService {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsLiveUpdateService.class);
    private static final List<String> LIVE_ANALYTICS_CACHE_NAMES = List.of(
            "orderAnalyticsPlatformSummary",
            "orderAnalyticsRevenueTrend",
            "orderAnalyticsTopProducts",
            "orderAnalyticsStatusBreakdown",
            "orderAnalyticsVendorSummary",
            "orderAnalyticsVendorRevenueTrend",
            "orderAnalyticsVendorTopProducts"
    );

    private final CacheManager cacheManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${analytics.live.redis-channel:analytics:live:dashboard:v1}")
    private String analyticsLiveRedisChannel;

    public void notifyOrderChangedAfterCommit(Order order, String trigger) {
        if (order == null || order.getId() == null) {
            return;
        }

        AnalyticsLiveDashboardMessage message = new AnalyticsLiveDashboardMessage(
                order.getId(),
                extractVendorIds(order),
                normalizeTrigger(trigger),
                Instant.now()
        );

        Runnable action = () -> {
            evictLiveAnalyticsCaches();
            publish(message);
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }

        action.run();
    }

    private Set<UUID> extractVendorIds(Order order) {
        if (order.getVendorOrders() == null || order.getVendorOrders().isEmpty()) {
            return Set.of();
        }
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (VendorOrder vendorOrder : order.getVendorOrders()) {
            if (vendorOrder != null && vendorOrder.getVendorId() != null) {
                vendorIds.add(vendorOrder.getVendorId());
            }
        }
        return Set.copyOf(vendorIds);
    }

    private String normalizeTrigger(String trigger) {
        if (!StringUtils.hasText(trigger)) {
            return "order_changed";
        }
        return trigger.trim();
    }

    private void evictLiveAnalyticsCaches() {
        for (String cacheName : LIVE_ANALYTICS_CACHE_NAMES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                continue;
            }
            cache.clear();
        }
    }

    private void publish(AnalyticsLiveDashboardMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(analyticsLiveRedisChannel, payload);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize live analytics message for order {}", message.orderId(), ex);
        } catch (RuntimeException ex) {
            log.warn("Failed to publish live analytics message for order {}", message.orderId(), ex);
        }
    }
}
