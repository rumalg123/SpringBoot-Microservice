package com.rumal.personalization_service.service;

import com.rumal.personalization_service.dto.EventType;
import com.rumal.personalization_service.dto.QueuedEventPayload;
import com.rumal.personalization_service.dto.RecordEventRequest;
import com.rumal.personalization_service.exception.ServiceUnavailableException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.Record;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventIngestionStreamService {

    private final StringRedisTemplate stringRedisTemplate;
    private final EventService eventService;
    private final TrackingOptOutService trackingOptOutService;

    private final AtomicBoolean consumerGroupReady = new AtomicBoolean(false);

    @Value("${spring.application.name:personalization-service}")
    private String applicationName;

    @Value("${personalization.ingestion.stream-key:person:events:v1}")
    private String streamKey;

    @Value("${personalization.ingestion.stream-group:person-event-persister}")
    private String streamGroup;

    @Value("${personalization.ingestion.stream-max-len:250000}")
    private long streamMaxLen;

    @Value("${personalization.ingestion.batch-size:200}")
    private int batchSize;

    @Value("${personalization.ingestion.claim-idle:30s}")
    private Duration claimIdleDuration;

    private String consumerName;

    @PostConstruct
    void initialize() {
        consumerName = applicationName + ":" + UUID.randomUUID();
        ensureConsumerGroup();
    }

    public void enqueue(UUID userId, String sessionId, List<RecordEventRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        if (userId != null && trackingOptOutService.hasOptedOut(userId)) {
            log.debug("Skipping enqueue for opted-out user {}", userId);
            return;
        }

        ensureConsumerGroup();

        StreamOperations<String, String, String> streamOperations = stringRedisTemplate.opsForStream();
        RedisStreamCommands.XAddOptions options = RedisStreamCommands.XAddOptions.maxlen(streamMaxLen)
                .approximateTrimming(true);

        try {
            for (RecordEventRequest request : requests) {
                Record<String, Map<String, String>> streamRecord = Record.of(toStreamFields(userId, sessionId, request))
                        .withStreamKey(streamKey);
                streamOperations.add(streamRecord, options);
            }
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Personalization ingestion unavailable", ex);
        }
    }

    @Scheduled(fixedDelayString = "${personalization.ingestion.poll-delay-ms:500}")
    public void drainQueuedEvents() {
        try {
            ensureConsumerGroup();
            processRecords(claimStaleRecords());
            processRecords(readNewRecords());
        } catch (Exception ex) {
            log.warn("Failed draining personalization event stream {}: {}", streamKey, ex.getMessage());
        }
    }

    private void ensureConsumerGroup() {
        if (consumerGroupReady.get()) {
            return;
        }

        synchronized (consumerGroupReady) {
            if (consumerGroupReady.get()) {
                return;
            }

            StreamOperations<String, String, String> streamOperations = stringRedisTemplate.opsForStream();
            RecordId bootstrapRecordId = null;

            try {
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(streamKey))) {
                    bootstrapRecordId = streamOperations.add(
                            Record.of(Map.of("_bootstrap", "1")).withStreamKey(streamKey),
                            RedisStreamCommands.XAddOptions.maxlen(1).approximateTrimming(true)
                    );
                }

                streamOperations.createGroup(streamKey, ReadOffset.latest(), streamGroup);
                log.info("Created personalization event stream group {} for {}", streamGroup, streamKey);
            } catch (Exception ex) {
                if (!isBusyGroup(ex)) {
                    throw new ServiceUnavailableException("Failed to initialize personalization event stream", ex);
                }
                log.debug("Personalization event stream group {} already exists for {}", streamGroup, streamKey);
            } finally {
                if (bootstrapRecordId != null) {
                    try {
                        streamOperations.delete(streamKey, bootstrapRecordId);
                    } catch (Exception ex) {
                        log.debug("Failed deleting bootstrap stream record {}: {}", bootstrapRecordId, ex.getMessage());
                    }
                }
            }

            consumerGroupReady.set(true);
        }
    }

    private List<MapRecord<String, String, String>> claimStaleRecords() {
        StreamOperations<String, String, String> streamOperations = stringRedisTemplate.opsForStream();
        PendingMessages pendingMessages = streamOperations.pending(
                streamKey,
                streamGroup,
                Range.unbounded(),
                batchSize,
                claimIdleDuration
        );

        if (pendingMessages.isEmpty()) {
            return List.of();
        }

        List<RecordId> recordIds = new ArrayList<>(pendingMessages.size());
        for (PendingMessage pendingMessage : pendingMessages) {
            recordIds.add(pendingMessage.getId());
        }

        return streamOperations.claim(
                streamKey,
                streamGroup,
                consumerName,
                claimIdleDuration,
                recordIds.toArray(RecordId[]::new)
        );
    }

    private List<MapRecord<String, String, String>> readNewRecords() {
        StreamOperations<String, String, String> streamOperations = stringRedisTemplate.opsForStream();
        List<MapRecord<String, String, String>> records = streamOperations.read(
                Consumer.from(streamGroup, consumerName),
                StreamReadOptions.empty().count(batchSize),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed())
        );
        return records == null ? List.of() : records;
    }

    private void processRecords(List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        List<MapRecord<String, String, String>> validRecords = new ArrayList<>();
        List<QueuedEventPayload> payloads = new ArrayList<>();
        List<MapRecord<String, String, String>> invalidRecords = new ArrayList<>();

        for (MapRecord<String, String, String> streamRecord : records) {
            QueuedEventPayload payload = toPayload(streamRecord);
            if (payload == null) {
                invalidRecords.add(streamRecord);
                continue;
            }
            validRecords.add(streamRecord);
            payloads.add(payload);
        }

        acknowledgeAndDelete(invalidRecords);
        if (validRecords.isEmpty()) {
            return;
        }

        try {
            eventService.persistBatch(payloads);
            acknowledgeAndDelete(validRecords);
        } catch (Exception ex) {
            log.warn("Failed processing {} personalization events: {}", validRecords.size(), ex.getMessage());
        }
    }

    private void acknowledgeAndDelete(List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        StreamOperations<String, String, String> streamOperations = stringRedisTemplate.opsForStream();
        RecordId[] recordIds = records.stream().map(MapRecord::getId).toArray(RecordId[]::new);
        try {
            streamOperations.acknowledge(streamKey, streamGroup, recordIds);
            streamOperations.delete(streamKey, recordIds);
        } catch (Exception ex) {
            log.warn("Failed acknowledging personalization event records: {}", ex.getMessage());
        }
    }

    private Map<String, String> toStreamFields(UUID userId, String sessionId, RecordEventRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", UUID.randomUUID().toString());
        fields.put("eventType", request.eventType().name());
        fields.put("productId", request.productId().toString());
        fields.put("enqueuedAt", Instant.now().toString());

        if (userId != null) {
            fields.put("userId", userId.toString());
        }
        if (StringUtils.hasText(sessionId)) {
            fields.put("sessionId", sessionId.trim());
        }
        if (StringUtils.hasText(request.categorySlugs())) {
            fields.put("categorySlugs", request.categorySlugs().trim());
        }
        if (request.vendorId() != null) {
            fields.put("vendorId", request.vendorId().toString());
        }
        if (StringUtils.hasText(request.brandName())) {
            fields.put("brandName", request.brandName().trim());
        }
        if (request.price() != null) {
            fields.put("price", request.price().toPlainString());
        }
        if (StringUtils.hasText(request.metadata())) {
            fields.put("metadata", request.metadata().trim());
        }
        return fields;
    }

    private QueuedEventPayload toPayload(MapRecord<String, String, String> streamRecord) {
        Map<String, String> fields = streamRecord.getValue();
        String eventId = fields.get("eventId");
        EventType eventType = parseEventType(fields.get("eventType"));
        UUID productId = parseUuid(fields.get("productId"));

        if (!StringUtils.hasText(eventId) || eventType == null || productId == null) {
            log.warn("Discarding malformed personalization event record {}", streamRecord.getId());
            return null;
        }

        return new QueuedEventPayload(
                eventId.trim(),
                parseUuid(fields.get("userId")),
                trimToNull(fields.get("sessionId")),
                eventType,
                productId,
                trimToNull(fields.get("categorySlugs")),
                parseUuid(fields.get("vendorId")),
                trimToNull(fields.get("brandName")),
                parseBigDecimal(fields.get("price")),
                trimToNull(fields.get("metadata")),
                parseInstant(fields.get("enqueuedAt"))
        );
    }

    private boolean isBusyGroup(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            if (containsBusyGroupMessage(current.getMessage()) || current.getClass().getSimpleName().contains("RedisBusyException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsBusyGroupMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toUpperCase();
        return normalized.contains("BUSYGROUP") || normalized.contains("GROUP NAME ALREADY EXISTS");
    }

    private EventType parseEventType(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        try {
            return EventType.valueOf(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID parseUuid(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant parseInstant(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Instant.now();
        }

        try {
            return Instant.parse(rawValue.trim());
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private String trimToNull(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        return rawValue.trim();
    }
}
