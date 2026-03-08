package com.rumal.vendor_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VendorMediaPrepareUploadItem(
        @NotNull VendorMediaAssetType assetType,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @Min(1) long sizeBytes
) {
}
