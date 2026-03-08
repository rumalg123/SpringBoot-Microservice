package com.rumal.order_service.service;

import java.util.UUID;

public interface OrderExportStorageService {

    StoredOrderExportFile store(UUID jobId, String fileName, String contentType, byte[] content);

    StoredOrderExportFile load(String storageKey, String fileName, String contentType);

    void delete(String storageKey);

    record StoredOrderExportFile(
            String storageKey,
            String fileName,
            String contentType,
            long contentLength,
            byte[] content
    ) {
    }
}
