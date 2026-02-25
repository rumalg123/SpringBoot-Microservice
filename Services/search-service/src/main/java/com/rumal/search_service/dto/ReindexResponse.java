package com.rumal.search_service.dto;

public record ReindexResponse(
        long totalIndexed,
        long durationMs,
        String status
) {}
