package com.rumal.inventory_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WarehouseCreateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        UUID vendorId,
        @NotNull String warehouseType,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 80) String city,
        @Size(max = 80) String state,
        @Size(max = 30) String postalCode,
        @Size(max = 2) String countryCode,
        @Size(max = 120) String contactName,
        @Size(max = 32) String contactPhone,
        @Size(max = 200) String contactEmail
) {}
