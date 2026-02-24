package com.rumal.order_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SetTrackingInfoRequest(
        @NotBlank @Size(max = 120) String trackingNumber,
        @Size(max = 500) String trackingUrl,
        @Size(max = 50) String carrierCode,
        LocalDate estimatedDeliveryDate
) {}
