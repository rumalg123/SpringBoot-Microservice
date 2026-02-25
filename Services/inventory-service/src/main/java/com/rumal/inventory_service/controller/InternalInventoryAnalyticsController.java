package com.rumal.inventory_service.controller;

import com.rumal.inventory_service.dto.analytics.*;
import com.rumal.inventory_service.security.InternalRequestVerifier;
import com.rumal.inventory_service.service.InventoryAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/inventory/analytics")
@RequiredArgsConstructor
public class InternalInventoryAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final InventoryAnalyticsService inventoryAnalyticsService;

    @GetMapping("/platform/health")
    public InventoryHealthSummary platformHealth(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return inventoryAnalyticsService.getPlatformHealth();
    }

    @GetMapping("/vendors/{vendorId}/health")
    public VendorInventoryHealth vendorHealth(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId) {
        internalRequestVerifier.verify(internalAuth);
        return inventoryAnalyticsService.getVendorHealth(vendorId);
    }

    @GetMapping("/platform/low-stock-alerts")
    public List<LowStockAlert> lowStockAlerts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "50") int limit) {
        internalRequestVerifier.verify(internalAuth);
        return inventoryAnalyticsService.getLowStockAlerts(limit);
    }
}
