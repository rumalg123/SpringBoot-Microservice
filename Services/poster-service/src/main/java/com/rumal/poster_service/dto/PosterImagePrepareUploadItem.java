package com.rumal.poster_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PosterImagePrepareUploadItem(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @Min(1) long sizeBytes
) {
}
