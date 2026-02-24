package com.rumal.product_service.dto;

import java.util.List;

public record BulkOperationResult(
        int totalRequested,
        int successCount,
        int failureCount,
        List<String> errors
) {}
