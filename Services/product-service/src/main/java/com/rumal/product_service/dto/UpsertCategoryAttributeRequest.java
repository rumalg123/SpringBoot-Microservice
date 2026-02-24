package com.rumal.product_service.dto;

import com.rumal.product_service.entity.AttributeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpsertCategoryAttributeRequest(
        @NotBlank(message = "attributeKey is required")
        @Size(max = 100, message = "attributeKey must be at most 100 characters")
        String attributeKey,

        @NotBlank(message = "attributeLabel is required")
        @Size(max = 150, message = "attributeLabel must be at most 150 characters")
        String attributeLabel,

        boolean required,

        Integer displayOrder,

        @NotNull(message = "attributeType is required")
        AttributeType attributeType,

        List<@Size(max = 200, message = "each allowed value must be at most 200 characters") String> allowedValues
) {}
