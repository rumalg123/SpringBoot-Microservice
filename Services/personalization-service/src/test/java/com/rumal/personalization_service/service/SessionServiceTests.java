package com.rumal.personalization_service.service;

import com.rumal.personalization_service.model.AnonymousSession;
import com.rumal.personalization_service.repository.AnonymousSessionRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTests {

    private final AnonymousSessionRepository anonymousSessionRepository = mock(AnonymousSessionRepository.class);
    private final UserEventRepository userEventRepository = mock(UserEventRepository.class);
    private final RecentlyViewedService recentlyViewedService = mock(RecentlyViewedService.class);
    private final RecommendationProfileService recommendationProfileService = mock(RecommendationProfileService.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache recommendationCache = mock(Cache.class);

    private final SessionService sessionService = new SessionService(
            anonymousSessionRepository,
            userEventRepository,
            recentlyViewedService,
            recommendationProfileService,
            cacheManager
    );

    @Test
    void mergeSessionCreatesMergedMarkerWhenAnonymousSessionRowDoesNotExistYet() {
        UUID userId = UUID.randomUUID();
        String sessionId = "anon-session";

        when(anonymousSessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        when(cacheManager.getCache("recommendations")).thenReturn(recommendationCache);

        sessionService.mergeSession(userId, sessionId);

        ArgumentCaptor<AnonymousSession> sessionCaptor = ArgumentCaptor.forClass(AnonymousSession.class);
        verify(anonymousSessionRepository).save(sessionCaptor.capture());
        AnonymousSession saved = sessionCaptor.getValue();

        assertEquals(sessionId, saved.getSessionId());
        assertEquals(userId, saved.getUserId());
        assertNotNull(saved.getMergedAt());
        assertNotNull(saved.getLastActivityAt());

        verify(recommendationProfileService).rememberMergedSession(userId, sessionId);
        verify(recommendationProfileService).mergeAnonymousToUser(userId, sessionId);
        verify(recentlyViewedService).mergeAnonymousToUser(userId, sessionId);
        verify(userEventRepository).mergeSessionEvents(userId, sessionId);
        verify(recommendationCache).evict("user::" + userId + "::20");
        verify(recommendationCache).evict("anon::" + sessionId + "::20");
    }
}
