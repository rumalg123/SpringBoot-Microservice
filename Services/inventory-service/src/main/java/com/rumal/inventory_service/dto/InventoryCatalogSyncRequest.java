package com.rumal.inventory_service.dto;

import java.util.UUID;

public record InventoryCatalogSyncRequest(
        UUID productId,
        UUID vendorId,
        String name,
        String sku,
        boolean active,
        boolean deleted
) {
}
