package com.rumal.admin_service.dto;

import java.util.List;
import java.util.UUID;

public record BulkOperationResult(
        int total,
        int succeeded,
        int failed,
        List<BulkItemError> errors
) {
    public record BulkItemError(UUID id, String error) {
    }
}
