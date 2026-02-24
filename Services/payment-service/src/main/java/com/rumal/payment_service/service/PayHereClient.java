package com.rumal.payment_service.service;

import com.rumal.payment_service.config.PayHereProperties;
import com.rumal.payment_service.exception.PayHereApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class PayHereClient {

    private final PayHereProperties props;
    private final RestClient.Builder restClientBuilder;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public PayHereClient(PayHereProperties props,
                         @Qualifier("restClientBuilder") RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * Get OAuth2 access token for PayHere Merchant API.
     * POST {baseUrl}/merchant/v1/oauth/token
     * Authorization: Basic Base64(appId:appSecret)
     * Body: grant_type=client_credentials
     *
     * Caches token for 500 seconds (PayHere tokens expire in ~600s).
     */
    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
                return cachedToken;
            }
            return refreshToken();
        }
    }

    @SuppressWarnings("unchecked")
    private String refreshToken() {
        String credentials = props.getAppId() + ":" + props.getAppSecret();
        String basicAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        try {
            Map<String, Object> response = restClientBuilder.build()
                    .post()
                    .uri(props.getBaseUrl() + "/merchant/v1/oauth/token")
                    .header("Authorization", "Basic " + basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials")
                    .retrieve()
                    .body(Map.class);

            if (response == null || !response.containsKey("access_token")) {
                throw new PayHereApiException("PayHere OAuth response missing access_token");
            }

            cachedToken = (String) response.get("access_token");
            // Default to 500s cache if expires_in not present
            int expiresIn = 500;
            if (response.containsKey("expires_in")) {
                Object exp = response.get("expires_in");
                if (exp instanceof Number n) {
                    expiresIn = Math.max(60, n.intValue() - 100); // buffer of 100s
                }
            }
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn);

            log.info("PayHere OAuth token refreshed, expires in {}s", expiresIn);
            return cachedToken;
        } catch (HttpClientErrorException ex) {
            throw new PayHereApiException("PayHere OAuth token request failed: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new PayHereApiException("PayHere OAuth token request failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Submit refund to PayHere.
     * POST {baseUrl}/merchant/v1/payment/refund
     * Authorization: Bearer {accessToken}
     * Body (JSON): { "payment_id": "...", "description": "...", "amount": ... (optional for partial) }
     *
     * Returns the response body as a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> refund(String payherePaymentId, BigDecimal amount, String description) {
        String token = getAccessToken();

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payment_id", payherePaymentId);
            body.put("description", description);
            if (amount != null) {
                body.put("amount", amount);
            }

            Map<String, Object> response = restClientBuilder.build()
                    .post()
                    .uri(props.getBaseUrl() + "/merchant/v1/payment/refund")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            log.info("PayHere refund response for payment {}: {}", payherePaymentId, response);
            return response;
        } catch (HttpClientErrorException ex) {
            log.error("PayHere refund failed for payment {}: {} - {}", payherePaymentId, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PayHereApiException("PayHere refund request failed: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new PayHereApiException("PayHere refund request failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retrieve payment details from PayHere.
     * GET {baseUrl}/merchant/v1/payment/search?order_id={orderId}
     * Authorization: Bearer {accessToken}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> retrievePayment(String orderId) {
        String token = getAccessToken();

        try {
            return restClientBuilder.build()
                    .get()
                    .uri(props.getBaseUrl() + "/merchant/v1/payment/search?order_id={orderId}", orderId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException ex) {
            log.error("PayHere payment retrieval failed for order {}: {}", orderId, ex.getResponseBodyAsString());
            throw new PayHereApiException("PayHere payment retrieval failed: " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new PayHereApiException("PayHere payment retrieval failed: " + ex.getMessage(), ex);
        }
    }
}
