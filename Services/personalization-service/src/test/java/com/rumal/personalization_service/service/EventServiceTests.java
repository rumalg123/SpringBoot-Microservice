package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.ProductClient;
import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.dto.EventType;
import com.rumal.personalization_service.dto.QueuedEventPayload;
import com.rumal.personalization_service.model.UserEvent;
import com.rumal.personalization_service.repository.AnonymousSessionRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventServiceTests {

    private final UserEventRepository userEventRepository = mock(UserEventRepository.class);
    private final AnonymousSessionRepository anonymousSessionRepository = mock(AnonymousSessionRepository.class);
    private final RecentlyViewedService recentlyViewedService = mock(RecentlyViewedService.class);
    private final TrackingOptOutService trackingOptOutService = mock(TrackingOptOutService.class);
    private final RecommendationProfileService recommendationProfileService = mock(RecommendationProfileService.class);
    private final ProductClient productClient = mock(ProductClient.class);

    private final EventService eventService = new EventService(
            userEventRepository,
            anonymousSessionRepository,
            recentlyViewedService,
            trackingOptOutService,
            recommendationProfileService,
            productClient
    );

    @Test
    void persistBatchResolvesMergedAnonymousSessionToCustomerBeforeSaving() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String sessionId = "anon-session";

        QueuedEventPayload payload = new QueuedEventPayload(
                UUID.randomUUID().toString(),
                null,
                sessionId,
                EventType.PRODUCT_VIEW,
                productId,
                null,
                null,
                null,
                null,
                null,
                Instant.now()
        );

        when(userEventRepository.findExistingExternalEventIds(any())).thenReturn(List.of());
        when(recommendationProfileService.resolveMergedUsers(Set.of(sessionId)))
                .thenReturn(Map.of(sessionId, userId));
        when(productClient.getBatchSummaries(List.of(productId)))
                .thenReturn(List.of(product(productId)));
        when(trackingOptOutService.hasOptedOut(userId)).thenReturn(false);

        eventService.persistBatch(List.of(payload));

        ArgumentCaptor<List<UserEvent>> eventCaptor = ArgumentCaptor.forClass(List.class);
        verify(userEventRepository).saveAll(eventCaptor.capture());
        UserEvent saved = eventCaptor.getValue().getFirst();

        assertEquals(userId, saved.getUserId());
        assertEquals("electronics", saved.getCategorySlugs());
        assertEquals("Acme", saved.getBrandName());

        verify(recentlyViewedService).add(userId, sessionId, productId);
        verify(recommendationProfileService).recordEvent(
                userId,
                sessionId,
                EventType.PRODUCT_VIEW,
                productId,
                Set.of("electronics"),
                "Acme",
                payload.enqueuedAt()
        );
        verify(anonymousSessionRepository, never()).saveAll(any());
    }

    private ProductSummary product(UUID productId) {
        return new ProductSummary(
                productId,
                "slug",
                "Product",
                "Short",
                "Acme",
                null,
                BigDecimal.valueOf(100),
                null,
                BigDecimal.valueOf(95),
                "SKU",
                "electronics",
                Set.of("electronics"),
                Set.of("electronics"),
                "SIMPLE",
                "APPROVED",
                UUID.randomUUID(),
                0L,
                0L,
                true,
                List.of(),
                5,
                "IN_STOCK",
                false
        );
    }
}
