package com.rumal.access_service.dto;

import com.rumal.access_service.entity.PermissionGroupScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 120) String keycloakId,
        @NotBlank @Size(max = 120) String name,
        @NotNull PermissionGroupScope scope,
        Set<String> permissions,
        Instant expiresAt
) {
}
