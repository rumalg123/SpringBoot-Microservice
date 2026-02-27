package com.rumal.personalization_service.service;

import com.rumal.personalization_service.model.AnonymousSession;
import com.rumal.personalization_service.repository.AnonymousSessionRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class SessionService {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final UserEventRepository userEventRepository;
    private final RecentlyViewedService recentlyViewedService;
    private final CacheManager cacheManager;

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void mergeSession(UUID userId, String sessionId) {
        var sessionOpt = anonymousSessionRepository.findBySessionIdAndMergedAtIsNull(sessionId);
        if (sessionOpt.isEmpty()) {
            log.debug("No unmerged anonymous session found for sessionId={}", sessionId);
            return;
        }

        AnonymousSession session = sessionOpt.get();

        int mergedEvents = userEventRepository.mergeSessionEvents(userId, sessionId);
        log.info("Merged {} anonymous events from session {} to user {}", mergedEvents, sessionId, userId);

        try {
            recentlyViewedService.mergeAnonymousToUser(userId, sessionId);
        } catch (Exception e) {
            log.error("Failed to merge recently-viewed for session {}", sessionId, e);
        }

        session.setUserId(userId);
        session.setMergedAt(Instant.now());
        anonymousSessionRepository.save(session);

        evictRecommendationsFor(userId, sessionId);

        log.info("Session {} merged to user {}", sessionId, userId);
    }

    private void evictRecommendationsFor(UUID userId, String sessionId) {
        Cache cache = cacheManager.getCache("recommendations");
        if (cache == null) return;
        // Evict for all commonly used limit values (controller caps at 100)
        for (int limit : List.of(5, 10, 20, 50, 100)) {
            cache.evict("user::" + userId + "::" + limit);
            cache.evict("anon::" + sessionId + "::" + limit);
        }
    }
}
