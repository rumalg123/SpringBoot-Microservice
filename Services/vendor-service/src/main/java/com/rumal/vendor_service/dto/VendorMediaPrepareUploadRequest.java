package com.rumal.vendor_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VendorMediaPrepareUploadRequest(
        @NotEmpty
        @Size(max = 2)
        List<@Valid VendorMediaPrepareUploadItem> files
) {
}
