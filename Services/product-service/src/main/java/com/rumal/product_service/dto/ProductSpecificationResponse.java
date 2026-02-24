package com.rumal.product_service.dto;

public record ProductSpecificationResponse(
        String key,
        String value,
        Integer displayOrder
) {}
