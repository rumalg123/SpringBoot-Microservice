package com.rumal.search_service.dto;

import java.util.List;

public record FacetGroup(
        String name,
        List<FacetBucket> buckets
) {}
