package com.rumal.inventory_service.controller;

import com.rumal.inventory_service.dto.*;
import com.rumal.inventory_service.exception.ResourceNotFoundException;
import com.rumal.inventory_service.exception.UnauthorizedException;
import com.rumal.inventory_service.security.InternalRequestVerifier;
import com.rumal.inventory_service.service.StockService;
import com.rumal.inventory_service.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventory/vendor/me")
@RequiredArgsConstructor
public class VendorInventoryController {

    private final WarehouseService warehouseService;
    private final StockService stockService;
    private final InternalRequestVerifier internalRequestVerifier;

    // ─── Warehouses ───

    @GetMapping("/warehouses")
    public Page<WarehouseResponse> listWarehouses(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return warehouseService.listByVendor(vendorId, pageable);
    }

    @GetMapping("/warehouses/{id}")
    public WarehouseResponse getWarehouse(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        WarehouseResponse warehouse = warehouseService.get(id);
        assertVendorOwnership(vendorId, warehouse.vendorId(), "warehouse", id);
        return warehouse;
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse createWarehouse(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @Valid @RequestBody WarehouseCreateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return warehouseService.createForVendor(vendorId, request);
    }

    @PutMapping("/warehouses/{id}")
    public WarehouseResponse updateWarehouse(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseUpdateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return warehouseService.updateForVendor(vendorId, id, request);
    }

    // ─── Stock ───

    @GetMapping("/stock")
    public Page<StockItemResponse> listStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return stockService.listStock(pageable, vendorId, null, null, null);
    }

    @GetMapping("/stock/{id}")
    public StockItemResponse getStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        StockItemResponse item = stockService.getStockItem(id);
        assertVendorOwnership(vendorId, item.vendorId(), "stock item", id);
        return item;
    }

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    public StockItemResponse createStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @Valid @RequestBody StockItemCreateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        StockItemCreateRequest vendorRequest = new StockItemCreateRequest(
                request.productId(), vendorId, request.warehouseId(),
                request.sku(), request.quantityOnHand(), request.lowStockThreshold(), request.backorderable()
        );
        return stockService.createStockItem(vendorRequest);
    }

    @PutMapping("/stock/{id}")
    public StockItemResponse updateStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID id,
            @Valid @RequestBody StockItemUpdateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        StockItemResponse existing = stockService.getStockItem(id);
        assertVendorOwnership(vendorId, existing.vendorId(), "stock item", id);
        return stockService.updateStockItem(id, request);
    }

    @PostMapping("/stock/{id}/adjust")
    public StockItemResponse adjustStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        StockItemResponse existing = stockService.getStockItem(id);
        assertVendorOwnership(vendorId, existing.vendorId(), "stock item", id);
        return stockService.adjustStock(id, request.quantityChange(), request.reason(), "vendor", userSub != null ? userSub : "unknown");
    }

    @PostMapping("/stock/bulk-import")
    public BulkStockImportResponse bulkImport(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @Valid @RequestBody BulkStockImportRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        // Override vendorId in all items
        var vendorItems = request.items().stream()
                .map(item -> new StockItemCreateRequest(
                        item.productId(), vendorId, item.warehouseId(),
                        item.sku(), item.quantityOnHand(), item.lowStockThreshold(), item.backorderable()
                ))
                .toList();
        return stockService.bulkImport(vendorItems, "vendor", userSub != null ? userSub : "unknown");
    }

    @GetMapping("/stock/low-stock")
    public Page<StockItemResponse> lowStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return stockService.listLowStockByVendor(vendorId, pageable);
    }

    // ─── Movements ───

    @GetMapping("/movements")
    public Page<StockMovementResponse> listMovements(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return stockService.listMovementsByVendor(vendorId, pageable);
    }

    // ─── Helpers ───

    private UUID requireVendorId(String vendorIdHeader) {
        if (!StringUtils.hasText(vendorIdHeader)) {
            throw new UnauthorizedException("Missing vendor identity");
        }
        try {
            return UUID.fromString(vendorIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid vendor identity");
        }
    }

    private void assertVendorOwnership(UUID vendorId, UUID resourceVendorId, String resourceType, UUID resourceId) {
        if (!vendorId.equals(resourceVendorId)) {
            throw new ResourceNotFoundException(resourceType + " not found: " + resourceId);
        }
    }
}
