package com.rumal.order_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.order_service.entity.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderAuditPayloadSanitizer {

    private static final int MAX_JSON_LENGTH = 12000;
    private static final int MAX_NOTE_LENGTH = 240;

    private final ObjectMapper objectMapper;

    public String sanitizeNote(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("Bearer ")
                || normalized.matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")) {
            return "[REDACTED]";
        }
        return normalized.length() <= MAX_NOTE_LENGTH ? normalized : normalized.substring(0, MAX_NOTE_LENGTH);
    }

    public String buildStatusChangeSet(OrderStatus fromStatus, OrderStatus toStatus, String note) {
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("status", fromStatus == null ? null : fromStatus.name());
        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("status", toStatus == null ? null : toStatus.name());
        afterState.put("note", sanitizeNote(note));
        return buildChangeSet(beforeState, afterState);
    }

    private String buildChangeSet(Object beforeValue, Object afterValue) {
        Object diff = buildDiff(beforeValue, afterValue);
        if (diff == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(diff);
            return json.length() <= MAX_JSON_LENGTH ? json : json.substring(0, MAX_JSON_LENGTH);
        } catch (Exception ex) {
            return null;
        }
    }

    private Object buildDiff(Object beforeValue, Object afterValue) {
        if (Objects.equals(beforeValue, afterValue)) {
            return null;
        }
        if (beforeValue instanceof Map<?, ?> beforeMap && afterValue instanceof Map<?, ?> afterMap) {
            Map<String, Object> diff = new LinkedHashMap<>();
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            beforeMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            afterMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            for (String key : keys) {
                Object nestedDiff = buildDiff(beforeMap.get(key), afterMap.get(key));
                if (nestedDiff != null) {
                    diff.put(key, nestedDiff);
                }
            }
            return diff.isEmpty() ? null : diff;
        }
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("before", beforeValue);
        change.put("after", afterValue);
        return change;
    }
}
