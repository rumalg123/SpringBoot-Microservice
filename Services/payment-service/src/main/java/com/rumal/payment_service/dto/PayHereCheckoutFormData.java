package com.rumal.payment_service.dto;

import java.util.UUID;

public record PayHereCheckoutFormData(
        UUID paymentId,
        String merchantId,
        String returnUrl,
        String cancelUrl,
        String notifyUrl,
        String orderId,
        String items,
        String currency,
        String amount,
        String hash,
        String firstName,
        String lastName,
        String email,
        String phone,
        String address,
        String city,
        String country,
        String checkoutUrl,
        String custom1,
        String custom2
) {}
