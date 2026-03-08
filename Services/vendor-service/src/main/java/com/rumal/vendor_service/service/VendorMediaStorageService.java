package com.rumal.vendor_service.service;

import com.rumal.vendor_service.dto.VendorMediaAssetType;
import com.rumal.vendor_service.dto.VendorMediaPrepareUploadRequest;
import com.rumal.vendor_service.dto.VendorMediaPrepareUploadResponse;

import java.util.UUID;

public interface VendorMediaStorageService {
    VendorMediaPrepareUploadResponse prepareUploads(UUID vendorId, VendorMediaPrepareUploadRequest request);
    String assertVendorMediaReady(UUID vendorId, VendorMediaAssetType assetType, String key);
    StoredImage getImage(String key);
}
