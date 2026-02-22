package com.rumal.admin_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorAdminOnboardRequest(
        @Size(max = 120) String keycloakUserId,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 120) String displayName,
        @Size(max = 20) String vendorUserRole,
        Boolean createIfMissing
) {
}
