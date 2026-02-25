package com.rumal.product_service.dto.analytics;

import java.util.UUID;

public record ProductSoldEntry(UUID id, String name, UUID vendorId, long soldCount) {}
