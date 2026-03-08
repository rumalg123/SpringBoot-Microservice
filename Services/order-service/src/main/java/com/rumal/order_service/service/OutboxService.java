package com.rumal.order_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.order_service.client.InventoryClient;
import com.rumal.order_service.client.PromotionClient;
import com.rumal.order_service.dto.StockCheckRequest;
import com.rumal.order_service.entity.OutboxEvent;
import com.rumal.order_service.entity.OutboxEventStatus;
import com.rumal.order_service.repo.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryClient inventoryClient;
    private final PromotionClient promotionClient;
    private final OrderSagaCompensationService orderSagaCompensationService;
    private final ObjectMapper objectMapper;

    @Value("${outbox.processor.processing-lease:PT2M}")
    private Duration processingLease;

    /**
     * Enqueue an outbox event within the current transaction.
     */
    public void enqueue(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = "{}";
            log.warn("Failed to serialize outbox payload for {}/{}", aggregateType, eventType, e);
        }
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .status(OutboxEventStatus.PENDING)
                .build();
        outboxEventRepository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> claimEventsForProcessing(Instant now, int limit) {
        List<OutboxEvent> events = outboxEventRepository.findEventsReadyToClaim(now, limit);
        if (events.isEmpty()) {
            return List.of();
        }

        Instant leaseExpiry = now.plus(processingLease == null ? Duration.ofMinutes(2) : processingLease);
        for (OutboxEvent event : events) {
            event.setStatus(OutboxEventStatus.PROCESSING);
            event.setNextRetryAt(leaseExpiry);
            event.setLastError(null);
        }
        outboxEventRepository.saveAll(events);
        return events.stream().map(OutboxEvent::getId).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId)
                .orElse(null);
        if (event == null) {
            return;
        }
        if (event.getStatus() != OutboxEventStatus.PROCESSING) {
            log.debug("Skipping outbox event {} because status is {}", event.getId(), event.getStatus());
            return;
        }

        try {
            dispatch(event);
            event.setStatus(OutboxEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            event.setNextRetryAt(null);
            event.setLastError(null);
            outboxEventRepository.save(event);
        } catch (Exception ex) {
            int nextRetryCount = event.getRetryCount() + 1;
            event.setRetryCount(nextRetryCount);
            event.setLastError(truncate(ex.getMessage()));
            if (nextRetryCount >= event.getMaxRetries()) {
                if (requiresSagaCompensation(event.getEventType())) {
                    orderSagaCompensationService.compensatePermanentFailure(event, ex.getMessage());
                }
                event.setStatus(OutboxEventStatus.FAILED);
                event.setNextRetryAt(null);
                log.error("Outbox event {} permanently failed after {} retries: {}/{}",
                        event.getId(), event.getRetryCount(), event.getAggregateType(), event.getEventType(), ex);
            } else {
                // Exponential backoff: 5s, 20s, 45s, 80s, 125s
                long delaySec = 5L * event.getRetryCount() * event.getRetryCount();
                event.setStatus(OutboxEventStatus.PENDING);
                event.setNextRetryAt(Instant.now().plusSeconds(delaySec));
                log.warn("Outbox event {} failed (attempt {}), next retry at {}: {}/{}",
                        event.getId(), event.getRetryCount(), event.getNextRetryAt(),
                        event.getAggregateType(), event.getEventType(), ex);
            }
            outboxEventRepository.save(event);
        }
    }

    private boolean requiresSagaCompensation(String eventType) {
        return "COUPON_COMMIT".equals(eventType) || "INVENTORY_RESERVE".equals(eventType);
    }

    private void dispatch(OutboxEvent event) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new com.fasterxml.jackson.core.type.TypeReference<>() {});

        switch (event.getEventType()) {
            case "RELEASE_COUPON_RESERVATION" -> {
                String reservationId = (String) payload.get("reservationId");
                String reason = (String) payload.get("reason");
                if (reservationId != null) {
                    promotionClient.releaseCouponReservation(UUID.fromString(reservationId), reason);
                }
            }
            case "CONFIRM_INVENTORY_RESERVATION" -> {
                inventoryClient.confirmReservation(event.getAggregateId());
            }
            case "RELEASE_INVENTORY_RESERVATION" -> {
                String reason = (String) payload.get("reason");
                inventoryClient.releaseReservation(event.getAggregateId(), reason);
            }
            case "COUPON_COMMIT" -> {
                String reservationId = (String) payload.get("reservationId");
                if (reservationId != null) {
                    promotionClient.commitCouponReservation(UUID.fromString(reservationId), event.getAggregateId());
                }
            }
            case "INVENTORY_RESERVE" -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> stockItems = (List<Map<String, Object>>) payload.get("stockItems");
                String expiresAtStr = (String) payload.get("expiresAt");
                if (stockItems != null && !stockItems.isEmpty()) {
                    List<StockCheckRequest> items = stockItems.stream()
                            .map(item -> new StockCheckRequest(
                                    UUID.fromString((String) item.get("productId")),
                                    ((Number) item.get("quantity")).intValue()))
                            .toList();
                    Instant expiresAt = expiresAtStr != null ? Instant.parse(expiresAtStr) : Instant.now().plusSeconds(1800);
                    inventoryClient.reserveStock(event.getAggregateId(), items, expiresAt);
                }
            }
            default -> log.warn("Unknown outbox event type: {}", event.getEventType());
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
