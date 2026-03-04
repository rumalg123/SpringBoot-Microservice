package com.rumal.payment_service.controller;

import com.rumal.payment_service.exception.ValidationException;
import com.rumal.payment_service.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@RestController
@RequestMapping("/webhooks/payhere")
@RequiredArgsConstructor
public class PayHereWebhookController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final PaymentService paymentService;

    @PostMapping(value = "/notify", consumes = {
            MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.ALL_VALUE
    })
    public ResponseEntity<String> notify(
            HttpServletRequest request,
            @RequestBody(required = false) String rawBody
    ) {
        Map<String, String> params = extractParams(request, rawBody);
        String merchantId = requireParam(params, "merchant_id");
        String orderId = requireParam(params, "order_id");
        String paymentId = requireParam(params, "payment_id");
        String payhereAmount = requireParam(params, "payhere_amount");
        String currency = requireParam(params, "payhere_currency");
        int statusCode = parseStatusCode(requireParam(params, "status_code"));
        String md5sig = requireParam(params, "md5sig");
        String method = trimToNull(params.get("method"));
        String cardHolderName = trimToNull(params.get("card_holder_name"));
        String cardNo = trimToNull(params.get("card_no"));
        String cardExpiry = trimToNull(params.get("card_expiry"));

        String rawPayload = buildRawPayload(request, rawBody, params);

        paymentService.processWebhook(
                merchantId, orderId, paymentId, payhereAmount, currency,
                statusCode, md5sig, method, cardHolderName, cardNo, cardExpiry, rawPayload
        );

        return ResponseEntity.ok("OK");
    }

    private Map<String, String> extractParams(HttpServletRequest request, String rawBody) {
        Map<String, String> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                String value = trimToNull(values[0]);
                if (value != null) {
                    result.put(key, value);
                }
            }
        });
        mergeUrlEncoded(result, request.getQueryString());
        mergeUrlEncoded(result, rawBody);
        mergeJson(result, rawBody);
        return result;
    }

    private void mergeUrlEncoded(Map<String, String> params, String raw) {
        String payload = trimToNull(raw);
        if (payload == null || !payload.contains("=")) {
            return;
        }
        String[] pairs = payload.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String keyEncoded = idx >= 0 ? pair.substring(0, idx) : pair;
            String valueEncoded = idx >= 0 ? pair.substring(idx + 1) : "";
            String key = trimToNull(urlDecode(keyEncoded));
            String value = trimToNull(urlDecode(valueEncoded));
            if (key != null && value != null) {
                params.putIfAbsent(key, value);
            }
        }
    }

    private void mergeJson(Map<String, String> params, String rawBody) {
        String payload = trimToNull(rawBody);
        if (payload == null || !payload.startsWith("{")) {
            return;
        }
        try {
            Map<String, Object> json = OBJECT_MAPPER.readValue(payload, new TypeReference<>() {});
            for (Map.Entry<String, Object> entry : json.entrySet()) {
                String key = trimToNull(entry.getKey());
                if (key == null || entry.getValue() == null) {
                    continue;
                }
                String value = trimToNull(String.valueOf(entry.getValue()));
                if (value != null) {
                    params.putIfAbsent(key, value);
                }
            }
        } catch (Exception ignored) {
            // Ignore malformed JSON payloads; validation below will report missing required fields.
        }
    }

    private String buildRawPayload(HttpServletRequest request, String rawBody, Map<String, String> params) {
        String payload = trimToNull(rawBody);
        if (payload != null) {
            return payload;
        }
        String query = trimToNull(request.getQueryString());
        if (query != null) {
            return query;
        }
        if (params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                joiner.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return joiner.toString();
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String requireParam(Map<String, String> params, String key) {
        String value = trimToNull(params.get(key));
        if (value == null) {
            throw new ValidationException("Missing required PayHere parameter: " + key);
        }
        return value;
    }

    private int parseStatusCode(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new ValidationException("Invalid PayHere status_code: " + raw);
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
