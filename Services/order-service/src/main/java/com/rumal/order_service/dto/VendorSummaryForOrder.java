package com.rumal.order_service.dto;

import java.util.UUID;

public record VendorSummaryForOrder(UUID id, String name, String slug) {}
