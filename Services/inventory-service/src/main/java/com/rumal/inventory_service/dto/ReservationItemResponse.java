package com.rumal.inventory_service.dto;

import java.util.UUID;

public record ReservationItemResponse(
        UUID reservationId,
        UUID productId,
        UUID warehouseId,
        int quantityReserved
) {}
