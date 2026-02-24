package com.rumal.admin_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkUpdateOrderStatusRequest(
        @NotEmpty @Size(max = 50) List<UUID> orderIds,
        @NotBlank String status
) {
}
