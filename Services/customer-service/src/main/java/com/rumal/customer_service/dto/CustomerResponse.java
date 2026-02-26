package com.rumal.customer_service.dto;

import com.rumal.customer_service.entity.CustomerLoyaltyTier;
import com.rumal.customer_service.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String keycloakId,
        String name,
        String email,
        String phone,
        String avatarUrl,
        LocalDate dateOfBirth,
        Gender gender,
        CustomerLoyaltyTier loyaltyTier,
        long loyaltyPoints,
        List<String> socialProviders,
        boolean active,
        Instant deactivatedAt,
        Instant createdAt,
        Instant updatedAt
) {}
