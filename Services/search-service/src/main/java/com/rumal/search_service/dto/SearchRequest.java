package com.rumal.search_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SearchRequest(
        String q,
        String category,
        String mainCategory,
        String subCategory,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        UUID vendorId,
        String sortBy,
        int page,
        int size
) {}
