package com.rumal.vendor_service.dto;

import java.util.List;

public record VendorMediaPrepareUploadResponse(List<VendorMediaPresignedUpload> uploads) {
}
