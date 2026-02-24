package com.rumal.admin_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertSystemConfigRequest(
        @NotBlank @Size(max = 200) String configKey,
        @Size(max = 4000) String configValue,
        @Size(max = 500) String description,
        @Size(max = 30) String valueType,
        Boolean active
) {
}
