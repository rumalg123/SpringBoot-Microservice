package com.rumal.analytics_service.service;

import com.rumal.analytics_service.dto.AnalyticsLiveDashboardMessage;
import com.rumal.analytics_service.dto.AnalyticsLiveRefreshEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class AnalyticsLiveStreamService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsLiveStreamService.class);
    private static final String ADMIN_SCOPE_KEY = "admin:global";
    private static final String EVENT_CONNECTED = "connected";
    private static final String EVENT_PING = "ping";
    private static final String EVENT_DASHBOARD_REFRESH = "dashboard-refresh";
    private static final List<String> ADMIN_CACHE_NAMES = List.of(
            "dashboardSummary",
            "revenueSummary",
            "topProducts",
            "vendorLeaderboard"
    );

    private final CacheManager cacheManager;
    private final long emitterTimeoutMs;
    private final Map<String, CopyOnWriteArraySet<SseEmitter>> emittersByScope = new ConcurrentHashMap<>();

    public AnalyticsLiveStreamService(
            CacheManager cacheManager,
            @Value("${analytics.live.emitter-timeout-ms:0}") long emitterTimeoutMs
    ) {
        this.cacheManager = cacheManager;
        this.emitterTimeoutMs = emitterTimeoutMs;
    }

    public SseEmitter registerAdminStream() {
        return registerEmitter(ADMIN_SCOPE_KEY, EVENT_CONNECTED, Map.of(
                "scope", "admin",
                "connectedAt", Instant.now().toString()
        ));
    }

    public SseEmitter registerVendorStream(UUID vendorId) {
        Objects.requireNonNull(vendorId, "vendorId is required");
        return registerEmitter(vendorScopeKey(vendorId), EVENT_CONNECTED, Map.of(
                "scope", "vendor",
                "vendorId", vendorId.toString(),
                "connectedAt", Instant.now().toString()
        ));
    }

    public void publishRefresh(AnalyticsLiveDashboardMessage message) {
        if (message == null) {
            return;
        }

        clearAdminCaches();
        broadcast(ADMIN_SCOPE_KEY, EVENT_DASHBOARD_REFRESH, new AnalyticsLiveRefreshEvent(
                "admin",
                null,
                normalizeTrigger(message.trigger()),
                message.occurredAt() == null ? Instant.now() : message.occurredAt()
        ));

        if (message.vendorIds() == null || message.vendorIds().isEmpty()) {
            clearVendorAnalyticsCache();
            return;
        }

        for (UUID vendorId : message.vendorIds()) {
            if (vendorId == null) {
                continue;
            }
            clearVendorAnalyticsCache(vendorId);
            broadcast(vendorScopeKey(vendorId), EVENT_DASHBOARD_REFRESH, new AnalyticsLiveRefreshEvent(
                    "vendor",
                    vendorId,
                    normalizeTrigger(message.trigger()),
                    message.occurredAt() == null ? Instant.now() : message.occurredAt()
            ));
        }
    }

    @Scheduled(fixedDelayString = "${analytics.live.heartbeat-interval-ms:25000}")
    public void sendHeartbeats() {
        Instant now = Instant.now();
        for (String scopeKey : emittersByScope.keySet()) {
            broadcast(scopeKey, EVENT_PING, Map.of("at", now.toString()));
        }
    }

    private SseEmitter registerEmitter(String scopeKey, String initialEventName, Object initialPayload) {
        SseEmitter emitter = emitterTimeoutMs <= 0 ? new SseEmitter(0L) : new SseEmitter(emitterTimeoutMs);
        emittersByScope.computeIfAbsent(scopeKey, ignored -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(scopeKey, emitter));
        emitter.onTimeout(() -> {
            removeEmitter(scopeKey, emitter);
            emitter.complete();
        });
        emitter.onError(error -> removeEmitter(scopeKey, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name(initialEventName)
                    .reconnectTime(3000)
                    .data(initialPayload));
        } catch (IOException ex) {
            removeEmitter(scopeKey, emitter);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    private void broadcast(String scopeKey, String eventName, Object payload) {
        CopyOnWriteArraySet<SseEmitter> emitters = emittersByScope.get(scopeKey);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(payload));
            } catch (IOException ex) {
                removeEmitter(scopeKey, emitter);
                try {
                    emitter.completeWithError(ex);
                } catch (RuntimeException completionEx) {
                    log.debug("Ignoring SSE completion failure for scope {}", scopeKey, completionEx);
                }
            }
        }
    }

    private void removeEmitter(String scopeKey, SseEmitter emitter) {
        CopyOnWriteArraySet<SseEmitter> emitters = emittersByScope.get(scopeKey);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByScope.remove(scopeKey, emitters);
        }
    }

    private void clearAdminCaches() {
        for (String cacheName : ADMIN_CACHE_NAMES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                continue;
            }
            cache.clear();
        }
    }

    private void clearVendorAnalyticsCache() {
        Cache cache = cacheManager.getCache("vendorAnalytics");
        if (cache != null) {
            cache.clear();
        }
    }

    private void clearVendorAnalyticsCache(UUID vendorId) {
        Cache cache = cacheManager.getCache("vendorAnalytics");
        if (cache != null) {
            cache.evict(vendorId);
        }
    }

    private String vendorScopeKey(UUID vendorId) {
        return "vendor:" + vendorId;
    }

    private String normalizeTrigger(String trigger) {
        return (trigger == null || trigger.isBlank()) ? "order_changed" : trigger.trim();
    }
}
