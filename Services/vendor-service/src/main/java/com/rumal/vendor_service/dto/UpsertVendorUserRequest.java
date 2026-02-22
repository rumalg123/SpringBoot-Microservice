package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorUserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertVendorUserRequest(
        @NotBlank @Size(max = 120) String keycloakUserId,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 120) String displayName,
        @NotNull VendorUserRole role,
        Boolean active
) {
}
