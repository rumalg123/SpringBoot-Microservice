package com.rumal.personalization_service.service;

import com.rumal.personalization_service.client.ProductClient;
import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.dto.QueuedEventPayload;
import com.rumal.personalization_service.model.AnonymousSession;
import com.rumal.personalization_service.model.UserEvent;
import com.rumal.personalization_service.repository.AnonymousSessionRepository;
import com.rumal.personalization_service.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class EventService {

    private final UserEventRepository userEventRepository;
    private final AnonymousSessionRepository anonymousSessionRepository;
    private final RecentlyViewedService recentlyViewedService;
    private final TrackingOptOutService trackingOptOutService;
    private final RecommendationProfileService recommendationProfileService;
    private final ProductClient productClient;

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 60)
    public void persistBatch(List<QueuedEventPayload> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Set<String> candidateEventIds = events.stream()
                .map(QueuedEventPayload::eventId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingEventIds = candidateEventIds.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(userEventRepository.findExistingExternalEventIds(candidateEventIds));

        Map<String, UUID> mergedSessionUsers = resolveMergedSessionUsers(events);
        Map<UUID, ProductSummary> productSummaries = loadProductSummaries(events);
        Instant activityAt = Instant.now();

        List<UserEvent> toPersist = new ArrayList<>();
        List<PersistedEventState> persistedStates = new ArrayList<>();
        Set<String> anonymousSessionIds = new LinkedHashSet<>();

        for (QueuedEventPayload event : events) {
            if (event == null || !StringUtils.hasText(event.eventId()) || existingEventIds.contains(event.eventId().trim())) {
                continue;
            }

            UUID resolvedUserId = resolveUserId(event, mergedSessionUsers);
            if (resolvedUserId != null && trackingOptOutService.hasOptedOut(resolvedUserId)) {
                log.debug("Skipping persisted event {} for opted-out user {}", event.eventId(), resolvedUserId);
                continue;
            }

            ProductSummary productSummary = productSummaries.get(event.productId());
            Set<String> categories = resolveCategories(productSummary, event);
            String categorySlugs = toCategoryCsv(categories, event.categorySlugs());
            String brandName = resolveBrandName(productSummary, event.brandName());
            UUID vendorId = productSummary != null && productSummary.vendorId() != null
                    ? productSummary.vendorId()
                    : event.vendorId();
            BigDecimal price = productSummary != null && productSummary.sellingPrice() != null
                    ? productSummary.sellingPrice()
                    : event.price();

            toPersist.add(UserEvent.builder()
                    .externalEventId(event.eventId().trim())
                    .userId(resolvedUserId)
                    .sessionId(truncate(event.sessionId(), 64))
                    .eventType(event.eventType().name())
                    .productId(event.productId())
                    .categorySlugs(truncate(categorySlugs, 500))
                    .vendorId(vendorId)
                    .brandName(truncate(brandName, 255))
                    .price(price)
                    .metadata(truncate(event.metadata(), 1000))
                    .createdAt(event.enqueuedAt() != null ? event.enqueuedAt() : activityAt)
                    .build());

            persistedStates.add(new PersistedEventState(
                    resolvedUserId,
                    truncate(event.sessionId(), 64),
                    event.productId(),
                    event.eventType().name(),
                    categories,
                    brandName,
                    event.enqueuedAt() != null ? event.enqueuedAt() : activityAt
            ));

            if (resolvedUserId == null && StringUtils.hasText(event.sessionId())) {
                anonymousSessionIds.add(event.sessionId().trim());
            }
        }

        if (toPersist.isEmpty()) {
            return;
        }

        userEventRepository.saveAll(toPersist);
        userEventRepository.flush();

        upsertAnonymousSessions(anonymousSessionIds, activityAt);
        updateReadModels(persistedStates);

        log.debug("Persisted {} personalization events ({} deduped, {} anonymous sessions touched)",
                toPersist.size(), existingEventIds.size(), anonymousSessionIds.size());
    }

    private Map<String, UUID> resolveMergedSessionUsers(List<QueuedEventPayload> events) {
        Set<String> sessionIds = events.stream()
                .filter(Objects::nonNull)
                .filter(event -> event.userId() == null)
                .map(QueuedEventPayload::sessionId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (sessionIds.isEmpty()) {
            return Map.of();
        }

        Map<String, UUID> resolved = new LinkedHashMap<>(recommendationProfileService.resolveMergedUsers(sessionIds));
        Set<String> unresolved = sessionIds.stream()
                .filter(sessionId -> !resolved.containsKey(sessionId))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (unresolved.isEmpty()) {
            return resolved;
        }

        for (AnonymousSession session : anonymousSessionRepository.findAllById(unresolved)) {
            if (session.getMergedAt() != null && session.getUserId() != null) {
                resolved.put(session.getSessionId(), session.getUserId());
            }
        }

        return resolved;
    }

    private Map<UUID, ProductSummary> loadProductSummaries(List<QueuedEventPayload> events) {
        List<UUID> productIds = events.stream()
                .map(QueuedEventPayload::productId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            return Map.of();
        }

        List<ProductSummary> summaries = productClient.getBatchSummaries(productIds);
        Map<UUID, ProductSummary> byId = new LinkedHashMap<>();
        for (ProductSummary summary : summaries) {
            byId.put(summary.id(), summary);
        }
        return byId;
    }

    private UUID resolveUserId(QueuedEventPayload event, Map<String, UUID> mergedSessionUsers) {
        if (event.userId() != null) {
            return event.userId();
        }
        if (!StringUtils.hasText(event.sessionId())) {
            return null;
        }
        return mergedSessionUsers.get(event.sessionId().trim());
    }

    private Set<String> resolveCategories(ProductSummary summary, QueuedEventPayload event) {
        if (summary != null && summary.categories() != null && !summary.categories().isEmpty()) {
            return new LinkedHashSet<>(summary.categories());
        }

        if (!StringUtils.hasText(event.categorySlugs())) {
            return Set.of();
        }

        return java.util.Arrays.stream(event.categorySlugs().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveBrandName(ProductSummary summary, String fallbackBrandName) {
        if (summary != null && StringUtils.hasText(summary.brandName())) {
            return summary.brandName().trim();
        }
        if (!StringUtils.hasText(fallbackBrandName)) {
            return null;
        }
        return fallbackBrandName.trim();
    }

    private String toCategoryCsv(Collection<String> categories, String fallbackValue) {
        if (categories != null && !categories.isEmpty()) {
            return categories.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.joining(","));
        }
        if (!StringUtils.hasText(fallbackValue)) {
            return null;
        }
        return fallbackValue.trim();
    }

    private void upsertAnonymousSessions(Set<String> sessionIds, Instant activityAt) {
        if (sessionIds.isEmpty()) {
            return;
        }

        Map<String, AnonymousSession> existing = new LinkedHashMap<>();
        for (AnonymousSession session : anonymousSessionRepository.findAllById(sessionIds)) {
            existing.put(session.getSessionId(), session);
        }

        List<AnonymousSession> toSave = new ArrayList<>();
        for (String sessionId : sessionIds) {
            AnonymousSession session = existing.get(sessionId);
            if (session == null) {
                toSave.add(AnonymousSession.builder()
                        .sessionId(sessionId)
                        .createdAt(activityAt)
                        .lastActivityAt(activityAt)
                        .build());
                continue;
            }
            if (session.getMergedAt() == null) {
                session.setLastActivityAt(activityAt);
                toSave.add(session);
            }
        }

        if (!toSave.isEmpty()) {
            anonymousSessionRepository.saveAll(toSave);
        }
    }

    private void updateReadModels(List<PersistedEventState> persistedStates) {
        for (PersistedEventState state : persistedStates) {
            recommendationProfileService.recordEvent(
                    state.userId(),
                    state.sessionId(),
                    com.rumal.personalization_service.dto.EventType.valueOf(state.eventType()),
                    state.productId(),
                    state.categories(),
                    state.brandName(),
                    state.occurredAt()
            );
            if ("PRODUCT_VIEW".equals(state.eventType())) {
                recentlyViewedService.add(state.userId(), state.sessionId(), state.productId());
            }
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private record PersistedEventState(
            UUID userId,
            String sessionId,
            UUID productId,
            String eventType,
            Set<String> categories,
            String brandName,
            Instant occurredAt
    ) {
    }
}
