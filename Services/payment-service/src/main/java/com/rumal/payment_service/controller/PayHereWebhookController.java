package com.rumal.payment_service.controller;

import com.rumal.payment_service.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.StringJoiner;

@RestController
@RequestMapping("/webhooks/payhere")
@RequiredArgsConstructor
public class PayHereWebhookController {

    private final PaymentService paymentService;

    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> notify(
            @RequestParam("merchant_id") String merchantId,
            @RequestParam("order_id") String orderId,
            @RequestParam("payment_id") String paymentId,
            @RequestParam("payhere_amount") String payhereAmount,
            @RequestParam("payhere_currency") String currency,
            @RequestParam("status_code") int statusCode,
            @RequestParam("md5sig") String md5sig,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "card_holder_name", required = false) String cardHolderName,
            @RequestParam(value = "card_no", required = false) String cardNo,
            @RequestParam(value = "card_expiry", required = false) String cardExpiry,
            HttpServletRequest request
    ) {
        String rawPayload = buildRawPayload(request);

        paymentService.processWebhook(
                merchantId, orderId, paymentId, payhereAmount, currency,
                statusCode, md5sig, method, cardHolderName, cardNo, cardExpiry, rawPayload
        );

        return ResponseEntity.ok("OK");
    }

    private String buildRawPayload(HttpServletRequest request) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                joiner.add(key + "=" + value);
            }
        }
        return joiner.toString();
    }
}
