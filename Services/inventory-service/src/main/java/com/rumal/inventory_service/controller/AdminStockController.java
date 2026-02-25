package com.rumal.inventory_service.controller;

import com.rumal.inventory_service.dto.*;
import com.rumal.inventory_service.entity.MovementType;
import com.rumal.inventory_service.entity.ReservationStatus;
import com.rumal.inventory_service.entity.StockStatus;
import com.rumal.inventory_service.security.InternalRequestVerifier;
import com.rumal.inventory_service.service.AdminInventoryAccessScopeService;
import com.rumal.inventory_service.service.AdminInventoryAccessScopeService.AdminActorScope;
import com.rumal.inventory_service.service.StockService;
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
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;
    private final AdminInventoryAccessScopeService adminAccessScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/stock")
    public Page<StockItemResponse> listStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) StockStatus stockStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        UUID scopedVendorId = adminAccessScopeService.resolveScopedVendorFilter(scope, vendorId);
        return stockService.listStock(pageable, scopedVendorId, productId, warehouseId, stockStatus);
    }

    @GetMapping("/stock/{id}")
    public StockItemResponse getStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        StockItemResponse item = stockService.getStockItem(id);
        adminAccessScopeService.assertCanManageStockItem(scope, item.vendorId());
        return item;
    }

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    public StockItemResponse createStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody StockItemCreateRequest request
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        return stockService.createStockItem(request);
    }

    @PutMapping("/stock/{id}")
    public StockItemResponse updateStockItem(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody StockItemUpdateRequest request
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        StockItemResponse existing = stockService.getStockItem(id);
        adminAccessScopeService.assertCanManageStockItem(scope, existing.vendorId());
        return stockService.updateStockItem(id, request);
    }

    @PostMapping("/stock/{id}/adjust")
    public StockItemResponse adjustStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustRequest request
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        StockItemResponse existing = stockService.getStockItem(id);
        adminAccessScopeService.assertCanManageStockItem(scope, existing.vendorId());
        return stockService.adjustStock(id, request.quantityChange(), request.reason(), "admin", userSub != null ? userSub : "unknown");
    }

    @PostMapping("/stock/bulk-import")
    public BulkStockImportResponse bulkImport(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody BulkStockImportRequest request
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        return stockService.bulkImport(request.items(), "admin", userSub != null ? userSub : "unknown");
    }

    @GetMapping("/stock/low-stock")
    public Page<StockItemResponse> lowStock(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        return stockService.listLowStock(pageable);
    }

    @GetMapping("/movements")
    public Page<StockMovementResponse> listMovements(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) MovementType movementType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        return stockService.listMovements(pageable, productId, warehouseId, movementType);
    }

    @GetMapping("/reservations")
    public Page<StockReservationDetailResponse> listReservations(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) UUID orderId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
        adminAccessScopeService.assertCanManageInventory(scope);
        return stockService.listReservations(pageable, status, orderId);
    }

    private AdminActorScope resolveScope(String internalAuth, String userSub, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        return adminAccessScopeService.resolveActorScope(userSub, userRoles, internalAuth);
    }
}
