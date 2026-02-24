package com.rumal.product_service.dto;

import java.util.List;

public record CsvImportResult(
        int totalRows,
        int successCount,
        int failureCount,
        List<String> errors
) {}
