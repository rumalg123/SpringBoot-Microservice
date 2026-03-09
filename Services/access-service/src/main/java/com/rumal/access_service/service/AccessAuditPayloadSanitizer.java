package com.rumal.access_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccessAuditPayloadSanitizer {

    private static final int MAX_JSON_LENGTH = 12000;

    private final ObjectMapper objectMapper;

    public String sanitizeEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        int atIndex = normalized.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        String local = normalized.substring(0, atIndex);
        String domain = normalized.substring(atIndex);
        return local.substring(0, Math.min(2, local.length())) + "***" + domain;
    }

    public String buildChangeSet(Object beforeState, Object afterState) {
        Object sanitizedBefore = sanitizeNode(convert(beforeState), null);
        Object sanitizedAfter = sanitizeNode(convert(afterState), null);
        Object diff = buildDiff(sanitizedBefore, sanitizedAfter);
        if (diff == null) {
            return null;
        }
        return serialize(diff);
    }

    private Object convert(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, Object.class);
    }

    private Object sanitizeNode(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return sanitizeCollection(collection, key);
        }
        if (value.getClass().isArray()) {
            return sanitizeArray(value, key);
        }
        if (value instanceof CharSequence sequence) {
            return sanitizeText(sequence, key);
        }
        return value;
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> map) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String childKey = entry.getKey() == null ? "null" : String.valueOf(entry.getKey());
            sanitized.put(childKey, sanitizeNode(entry.getValue(), childKey));
        }
        return sanitized;
    }

    private List<Object> sanitizeCollection(Collection<?> collection, String key) {
        List<Object> sanitized = new ArrayList<>(collection.size());
        for (Object item : collection) {
            sanitized.add(sanitizeNode(item, key));
        }
        return sanitized;
    }

    private List<Object> sanitizeArray(Object value, String key) {
        int length = Array.getLength(value);
        List<Object> sanitized = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            sanitized.add(sanitizeNode(Array.get(value, i), key));
        }
        return sanitized;
    }

    private Object sanitizeText(CharSequence sequence, String key) {
        String stringValue = sequence.toString().trim();
        String normalizedKey = normalizeKey(key);
        if (normalizedKey.contains("email")) {
            return sanitizeEmail(stringValue);
        }
        if (normalizedKey.contains("token") || normalizedKey.contains("secret") || normalizedKey.contains("password")) {
            return "[REDACTED]";
        }
        return stringValue;
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private Object buildDiff(Object beforeValue, Object afterValue) {
        if (Objects.equals(beforeValue, afterValue)) {
            return null;
        }
        if (beforeValue instanceof Map<?, ?> beforeMap && afterValue instanceof Map<?, ?> afterMap) {
            Map<String, Object> diff = new LinkedHashMap<>();
            Set<String> keys = new LinkedHashSet<>();
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

    private String serialize(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() <= MAX_JSON_LENGTH) {
                return json;
            }
            return json.substring(0, MAX_JSON_LENGTH);
        } catch (Exception ex) {
            return null;
        }
    }
}
