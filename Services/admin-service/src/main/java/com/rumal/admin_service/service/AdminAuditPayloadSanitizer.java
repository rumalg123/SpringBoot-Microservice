package com.rumal.admin_service.service;

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
public class AdminAuditPayloadSanitizer {

    private static final int MAX_DETAILS_LENGTH = 2000;
    private static final int MAX_JSON_LENGTH = 12000;
    private static final Set<String> REDACTED_KEY_PARTS = Set.of(
            "password",
            "passwd",
            "passcode",
            "secret",
            "token",
            "jwt",
            "authorization",
            "cookie",
            "session",
            "cvv",
            "cvc",
            "cardnumber",
            "card_no",
            "cardno",
            "iban",
            "accountnumber",
            "bankaccount",
            "routing",
            "sortcode"
    );
    private static final Set<String> MASKED_KEY_PARTS = Set.of(
            "email",
            "phone"
    );

    private final ObjectMapper objectMapper;

    public String sanitizeDetails(String details) {
        if (!StringUtils.hasText(details)) {
            return null;
        }
        String normalized = details.trim();
        if (looksLikeToken(normalized)) {
            normalized = "[REDACTED]";
        }
        if (normalized.length() <= MAX_DETAILS_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_DETAILS_LENGTH);
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
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childKey = entry.getKey() == null ? "null" : String.valueOf(entry.getKey());
                sanitized.put(childKey, sanitizeNode(entry.getValue(), childKey));
            }
            return sanitized;
        }

        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            for (Object item : collection) {
                sanitized.add(sanitizeNode(item, key));
            }
            return sanitized;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                sanitized.add(sanitizeNode(Array.get(value, i), key));
            }
            return sanitized;
        }

        if (value instanceof CharSequence sequence) {
            return sanitizeString(sequence.toString(), key);
        }

        return value;
    }

    private String sanitizeString(String value, String key) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String normalized = value.trim();
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);

        for (String token : REDACTED_KEY_PARTS) {
            if (normalizedKey.contains(token)) {
                return "[REDACTED]";
            }
        }

        for (String token : MASKED_KEY_PARTS) {
            if (normalizedKey.contains(token)) {
                return maskValue(normalized);
            }
        }

        if (looksLikeToken(normalized)) {
            return "[REDACTED]";
        }
        return normalized;
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

    private boolean looksLikeToken(String value) {
        return value.startsWith("Bearer ")
                || value.matches("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");
    }

    private String maskValue(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        int visible = Math.min(2, value.length() / 4);
        return value.substring(0, visible) + "*".repeat(Math.max(2, value.length() - visible - 2)) + value.substring(value.length() - 2);
    }
}
