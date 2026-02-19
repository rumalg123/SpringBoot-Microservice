package com.rumal.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductImageNameRequest(
        @NotEmpty(message = "fileNames cannot be empty")
        @Size(max = 5, message = "fileNames cannot contain more than 5 items")
        List<@NotBlank(message = "fileName cannot be blank") String> fileNames
) {
}
