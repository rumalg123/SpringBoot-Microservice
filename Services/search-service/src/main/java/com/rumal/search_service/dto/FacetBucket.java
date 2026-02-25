package com.rumal.search_service.dto;

public record FacetBucket(
        String key,
        long docCount
) {}
