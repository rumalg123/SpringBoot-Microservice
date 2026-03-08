package com.rumal.vendor_service.dto;

import java.time.Instant;

public record VendorMediaPresignedUpload(
        VendorMediaAssetType assetType,
        String key,
        String uploadUrl,
        String contentType,
        Instant expiresAt
) {
}
