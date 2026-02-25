package com.rumal.product_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BatchProductRequest(
        @NotEmpty @Size(max = 50) List<UUID> productIds
) {}
