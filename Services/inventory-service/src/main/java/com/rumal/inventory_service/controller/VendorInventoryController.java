package com.rumal.inventory_service.controller;

import com.rumal.inventory_service.dto.*;
import com.rumal.inventory_service.exception.ResourceNotFoundException;
import com.rumal.inventory_service.security.InternalRequestVerifier;
import com.rumal.inventory_service.service.AdminInventoryAccessScopeService;
import com.rumal.inventory_service.service.AdminInventoryAccessScopeService.AdminActorScope;
import com.rumal.inventory_service.service.StockService;
import com.rumal.inventory_service.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/inventory/vendor/me")
@RequiredArgsConstructor
public class VendorInventoryController {

    private final WarehouseService warehouseService;
    private final StockService stockService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminInventoryAccessScopeService accessScopeService;

    // ─── Warehouses ───

    @GetMapping("/warehouses")
    public Page<WarehouseResponse> listWarehouses(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        return warehouseService.listByVendor(resolvedVendorId, pageable);
    }

    @GetMapping("/warehouses/{id}")
    public WarehouseResponse getWarehouse(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        WarehouseResponse warehouse = warehouseService.get(id);
        assertVendorOwnership(resolvedVendorId, warehouse.vendorId(), "warehouse", id);
        return warehouse;
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse createWarehouse(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody WarehouseCreateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        return warehouseService.createForVendor(resolvedVendorId, request);
    }

    @PutMapping("/warehouses/{id}")
    public WarehouseResponse updateWarehouse(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id,
            @Valid @RequestBody WarehouseUpdateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        return warehouseService.updateForVendor(resolvedVendorId, id, request);
    }

    // ─── Stock ───

    @GetMapping("/stock")
    public Page<StockItemResponse> listStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        return stockService.listStock(pageable, resolvedVendorId, null, null, null);
    }

    @GetMapping("/stock/{id}")
    public StockItemResponse getStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        StockItemResponse item = stockService.getStockItem(id);
        assertVendorOwnership(resolvedVendorId, item.vendorId(), "stock item", id);
        return item;
    }

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    public StockItemResponse createStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody StockItemCreateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        // C-03: Validate warehouse belongs to this vendor before creating stock
        warehouseService.assertWarehouseOwnedByVendor(request.warehouseId(), resolvedVendorId);
        StockItemCreateRequest vendorRequest = new StockItemCreateRequest(
                request.productId(), resolvedVendorId, request.warehouseId(),
                request.sku(), request.quantityOnHand(), request.lowStockThreshold(), request.backorderable()
        );
        return stockService.createStockItem(vendorRequest);
    }

    @PutMapping("/stock/{id}")
    public StockItemResponse updateStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id,
            @Valid @RequestBody StockItemUpdateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        StockItemResponse existing = stockService.getStockItem(id);
        assertVendorOwnership(resolvedVendorId, existing.vendorId(), "stock item", id);
        return stockService.updateStockItem(id, request);
    }

    @PostMapping("/stock/{id}/adjust")
    public StockItemResponse adjustStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        StockItemResponse existing = stockService.getStockItem(id);
        assertVendorOwnership(resolvedVendorId, existing.vendorId(), "stock item", id);
        return stockService.adjustStock(id, request.quantityChange(), request.reason(), "vendor", userSub != null ? userSub : "unknown");
    }

    @PostMapping("/stock/bulk-import")
    public BulkStockImportResponse bulkImport(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody BulkStockImportRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        // C-03: Validate all warehouse IDs belong to this vendor before bulk import
        request.items().stream()
                .map(StockItemCreateRequest::warehouseId)
                .distinct()
                .forEach(whId -> warehouseService.assertWarehouseOwnedByVendor(whId, resolvedVendorId));
        var vendorItems = request.items().stream()
                .map(item -> new StockItemCreateRequest(
                        item.productId(), resolvedVendorId, item.warehouseId(),
                        item.sku(), item.quantityOnHand(), item.lowStockThreshold(), item.backorderable()
                ))
                .toList();
        return stockService.bulkImport(vendorItems, "vendor", userSub != null ? userSub : "unknown");
    }

    @GetMapping("/stock/low-stock")
    public Page<StockItemResponse> lowStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        return stockService.listLowStockByVendor(resolvedVendorId, pageable);
    }

    // ─── Movements ───

    @GetMapping("/movements")
    public Page<StockMovementResponse> listMovements(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, internalAuth, vendorId);
        return stockService.listMovementsByVendor(resolvedVendorId, pageable);
    }

    // ─── Helpers ───

    private UUID resolveVendorId(String userSub, String userRoles, String internalAuth, UUID requestedVendorId) {
        AdminActorScope scope = accessScopeService.resolveActorScope(userSub, userRoles, internalAuth);
        return accessScopeService.resolveScopedVendorFilter(scope, requestedVendorId);
    }

    private void assertVendorOwnership(UUID vendorId, UUID resourceVendorId, String resourceType, UUID resourceId) {
        if (!vendorId.equals(resourceVendorId)) {
            throw new ResourceNotFoundException(resourceType + " not found: " + resourceId);
        }
    }
}
