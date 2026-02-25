package com.rumal.search_service.client.dto;

import java.util.List;

public record ProductIndexPage(
        List<ProductIndexData> content,
        int number,
        int totalPages,
        long totalElements,
        boolean last
) {}
