package com.rumal.product_service.dto;

import com.rumal.product_service.entity.AttributeType;

import java.util.List;
import java.util.UUID;

public record CategoryAttributeResponse(
        UUID id,
        String attributeKey,
        String attributeLabel,
        boolean required,
        Integer displayOrder,
        AttributeType attributeType,
        List<String> allowedValues
) {}
