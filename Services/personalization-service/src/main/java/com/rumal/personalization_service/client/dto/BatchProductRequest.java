package com.rumal.personalization_service.client.dto;

import java.util.List;
import java.util.UUID;

public record BatchProductRequest(List<UUID> productIds) {}
