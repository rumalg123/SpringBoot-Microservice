package com.rumal.personalization_service.service;

import com.rumal.personalization_service.model.AnonymousSession;
import com.rumal.personalization_service.repository.AnonymousSessionRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class SessionService {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final UserEventRepository userEventRepository;
    private final RecentlyViewedService recentlyViewedService;

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    @CacheEvict(cacheNames = "recommendations", allEntries = true)
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
            log.warn("Failed to merge recently-viewed Redis data for session {}: {}", sessionId, e.getMessage());
        }

        session.setUserId(userId);
        session.setMergedAt(Instant.now());
        anonymousSessionRepository.save(session);

        log.info("Session {} merged to user {}", sessionId, userId);
    }
}
