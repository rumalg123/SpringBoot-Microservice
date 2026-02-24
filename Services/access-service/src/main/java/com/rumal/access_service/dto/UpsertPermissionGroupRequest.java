package com.rumal.access_service.dto;

import com.rumal.access_service.entity.PermissionGroupScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpsertPermissionGroupRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotNull Set<String> permissions,
        @NotNull PermissionGroupScope scope
) {
}
