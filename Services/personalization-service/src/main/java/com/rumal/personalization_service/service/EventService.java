package com.rumal.personalization_service.service;

import com.rumal.personalization_service.dto.RecordEventRequest;
import com.rumal.personalization_service.model.AnonymousSession;
import com.rumal.personalization_service.model.UserEvent;
import com.rumal.personalization_service.repository.AnonymousSessionRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.rumal.personalization_service.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class EventService {

    private final UserEventRepository userEventRepository;
    private final AnonymousSessionRepository anonymousSessionRepository;
    private final RecentlyViewedService recentlyViewedService;
    private final TrackingOptOutService trackingOptOutService;

    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public void recordEvent(UUID userId, String sessionId, RecordEventRequest request) {
        if (userId == null && (sessionId == null || sessionId.isBlank())) {
            throw new ValidationException("Either userId or sessionId must be provided");
        }

        // GDPR: respect user's tracking opt-out preference
        if (userId != null && trackingOptOutService.hasOptedOut(userId)) {
            log.debug("Skipping event recording for opted-out user {}", userId);
            return;
        }

        UserEvent event = UserEvent.builder()
                .userId(userId)
                .sessionId(sessionId)
                .eventType(request.eventType().name())
                .productId(request.productId())
                .categorySlugs(request.categorySlugs())
                .vendorId(request.vendorId())
                .brandName(request.brandName())
                .price(request.price())
                .metadata(request.metadata())
                .build();

        userEventRepository.save(event);

        if (request.eventType() == com.rumal.personalization_service.dto.EventType.PRODUCT_VIEW) {
            recentlyViewedService.add(userId, sessionId, request.productId());
        }

        if (sessionId != null && userId == null) {
            upsertAnonymousSession(sessionId);
        }

        log.debug("Recorded {} event for product {} (user={}, session={})",
                request.eventType(), request.productId(), userId, sessionId);
    }

    private void upsertAnonymousSession(String sessionId) {
        anonymousSessionRepository.findById(sessionId).ifPresentOrElse(
                session -> {
                    session.setLastActivityAt(Instant.now());
                    anonymousSessionRepository.save(session);
                },
                () -> anonymousSessionRepository.save(AnonymousSession.builder()
                        .sessionId(sessionId)
                        .build())
        );
    }
}
