package com.rumal.inventory_service.dto;

import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String name,
        String description,
        UUID vendorId,
        String warehouseType,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String countryCode,
        String contactName,
        String contactPhone,
        String contactEmail,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
