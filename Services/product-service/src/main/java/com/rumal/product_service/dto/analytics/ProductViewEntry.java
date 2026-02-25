package com.rumal.product_service.dto.analytics;

import java.util.UUID;

public record ProductViewEntry(UUID id, String name, UUID vendorId, long viewCount) {}
