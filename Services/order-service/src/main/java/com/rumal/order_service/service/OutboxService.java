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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    private final ObjectMapper objectMapper;

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
    public void processEvent(OutboxEvent event) {
        try {
            dispatch(event);
            event.setStatus(OutboxEventStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);
        } catch (Exception ex) {
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(truncate(ex.getMessage()));
            if (event.getRetryCount() >= event.getMaxRetries()) {
                event.setStatus(OutboxEventStatus.FAILED);
                log.error("Outbox event {} permanently failed after {} retries: {}/{}",
                        event.getId(), event.getRetryCount(), event.getAggregateType(), event.getEventType(), ex);
            } else {
                // Exponential backoff: 5s, 20s, 45s, 80s, 125s
                long delaySec = 5L * event.getRetryCount() * event.getRetryCount();
                event.setNextRetryAt(Instant.now().plusSeconds(delaySec));
                log.warn("Outbox event {} failed (attempt {}), next retry at {}: {}/{}",
                        event.getId(), event.getRetryCount(), event.getNextRetryAt(),
                        event.getAggregateType(), event.getEventType(), ex);
            }
            outboxEventRepository.save(event);
        }
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
