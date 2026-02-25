package com.rumal.search_service.dto;

import java.util.List;

public record SearchResponse(
        List<SearchHit> content,
        List<FacetGroup> facets,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String query,
        long tookMs
) {}
