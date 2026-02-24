package com.rumal.payment_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PayHereHashUtil {

    private PayHereHashUtil() {}

    /**
     * Generate checkout hash for PayHere form submission.
     * Formula: MD5_UPPER(merchantId + orderId + formattedAmount + currency + MD5_UPPER(merchantSecret))
     */
    public static String generateCheckoutHash(String merchantId, String orderId,
                                               BigDecimal amount, String currency,
                                               String merchantSecret) {
        String formattedAmount = formatAmount(amount);
        String hashedSecret = md5Upper(merchantSecret);
        return md5Upper(merchantId + orderId + formattedAmount + currency + hashedSecret);
    }

    /**
     * Verify webhook md5sig from PayHere notification.
     * Formula: MD5_UPPER(merchant_id + order_id + amount + currency + status_code + MD5_UPPER(merchantSecret))
     */
    public static boolean verifyWebhookSignature(String merchantId, String orderId,
                                                  String amount, String currency,
                                                  int statusCode, String merchantSecret,
                                                  String receivedSig) {
        String hashedSecret = md5Upper(merchantSecret);
        String computed = md5Upper(merchantId + orderId + amount + currency + statusCode + hashedSecret);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                receivedSig.getBytes(StandardCharsets.UTF_8)
        );
    }

    static String md5Upper(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02X", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    /**
     * Format amount to 2 decimal places as PayHere expects (e.g. "100.00")
     */
    static String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
